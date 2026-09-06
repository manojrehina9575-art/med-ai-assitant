#!/usr/bin/env node
/**
 * Builds the Skeleton Viewer V1 GLB from the official BodyParts3D 4.0 archive.
 *
 *   node scripts/anatomy/build-bodyparts3d-skeleton.mjs [--archive <zip>] [--isa-archive <zip>] [--out <glb>] [--check]
 *
 * Reproducible and dependency-free: reads the official zip directly (stored/deflated members via
 * zlib), parses the Wavefront OBJ element files named in bodyparts3d-elements.json, applies one
 * documented rigid transform, and writes a GLB plus a metadata JSON the frontend tests assert
 * against. Nothing is edited by hand afterwards.
 *
 * Geometry is never mirrored. Mirroring would turn a right bone into a left-shaped one; the only
 * transform applied is a proper rotation (det = +1), a uniform scale and a translation.
 */
import { createHash } from 'node:crypto';
import { inflateRawSync } from 'node:zlib';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '../..');

const DEFAULTS = {
  partofArchive: resolve(REPO, '.anatomy-source-cache/partof_BP3D_4.0_obj_99.zip'),
  isaArchive: resolve(REPO, '.anatomy-source-cache/isa_BP3D_4.0_obj_99.zip'),
  out: resolve(REPO, 'frontend/public/models/anatomy/bodyparts3d-skeleton-v1.glb'),
  metadata: resolve(
    REPO,
    'frontend/src/components/anatomy-viewer/model/bodyparts3dSkeletonModel.json'
  ),
};

/**
 * Source space, established empirically from the officially sided element files and confirmed by
 * three independent anatomical relationships (clavicle vs scapula, patella vs tibia, talus vs
 * calcaneus):
 *   +X = anatomical LEFT      (right humerus centroid x < 0, left humerus centroid x > 0)
 *   +Y = POSTERIOR            (anterior structures sit at more negative Y)
 *   +Z = SUPERIOR             (clavicle near z = 1355, talus near z = -31)
 *   units = millimetres
 *
 * Viewer space wants Y up and the body facing +Z, with anatomical right on negative X (screen
 * left). The rotation below is +90 degrees about X — a proper rotation, no reflection:
 *   x_v = x_s        y_v = z_s        z_v = -y_s
 * followed by a uniform mm -> m scale and a translation that centres the bounding box.
 */
const MM_TO_UNITS = 0.001;

function transformPoint(x, y, z) {
  return [x * MM_TO_UNITS, z * MM_TO_UNITS, -y * MM_TO_UNITS];
}

function transformNormal(x, y, z) {
  // Same rotation; a uniform scale leaves normals unchanged.
  return [x, z, -y];
}

// ---------------------------------------------------------------- zip reading

/** Minimal reader for the members we need. Handles stored (0) and deflated (8) entries. */
function readZipMembers(zipPath, wanted) {
  const buffer = readFileSync(zipPath);
  const eocd = findEndOfCentralDirectory(buffer);
  const count = buffer.readUInt16LE(eocd + 10);
  let offset = buffer.readUInt32LE(eocd + 16);
  const found = new Map();

  for (let i = 0; i < count; i++) {
    if (buffer.readUInt32LE(offset) !== 0x02014b50) {
      throw new Error(`Corrupt central directory entry at ${offset}`);
    }
    const method = buffer.readUInt16LE(offset + 10);
    const compressedSize = buffer.readUInt32LE(offset + 20);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const localOffset = buffer.readUInt32LE(offset + 42);
    const name = buffer.toString('utf8', offset + 46, offset + 46 + nameLength);
    const base = name.split('/').pop();

    if (wanted.has(base)) {
      found.set(base, extractMember(buffer, localOffset, method, compressedSize));
    }
    offset += 46 + nameLength + extraLength + commentLength;
  }

  const missing = [...wanted].filter((name) => !found.has(name));
  if (missing.length > 0) {
    throw new Error(`Archive is missing expected element files: ${missing.join(', ')}`);
  }
  return found;
}

function findEndOfCentralDirectory(buffer) {
  for (let i = buffer.length - 22; i >= 0; i--) {
    if (buffer.readUInt32LE(i) === 0x06054b50) return i;
  }
  throw new Error('Not a zip archive: end-of-central-directory record not found');
}

function extractMember(buffer, localOffset, method, compressedSize) {
  if (buffer.readUInt32LE(localOffset) !== 0x04034b50) {
    throw new Error(`Corrupt local header at ${localOffset}`);
  }
  const nameLength = buffer.readUInt16LE(localOffset + 26);
  const extraLength = buffer.readUInt16LE(localOffset + 28);
  const start = localOffset + 30 + nameLength + extraLength;
  const raw = buffer.subarray(start, start + compressedSize);

  if (method === 0) return raw;
  if (method === 8) return inflateRawSync(raw);
  throw new Error(`Unsupported zip compression method ${method}`);
}

// ---------------------------------------------------------------- OBJ parsing

/**
 * Parses a BodyParts3D element OBJ.
 *
 * These files carry one normal per vertex with `f v//vn` faces where the two indices agree, so the
 * dataset's own normals are used rather than recomputed.
 */
function parseObj(text) {
  const positions = [];
  const normals = [];
  const indices = [];
  let representationId = null;
  let fileId = null;
  let licenseNotice = null;

  for (const rawLine of text.split('\n')) {
    const line = rawLine.trim();
    if (line.length === 0) continue;

    if (line.startsWith('#')) {
      const repMatch = line.match(/Representation ID\s*:\s*(BP\d+)/i);
      if (repMatch) representationId = repMatch[1];
      const fileMatch = line.match(/File ID\s*:\s*(FJ\d+)/i);
      if (fileMatch) fileId = fileMatch[1];
      if (/creative commons/i.test(line) && !licenseNotice) licenseNotice = line.replace(/^#\s*/, '');
      continue;
    }

    const parts = line.split(/\s+/);
    switch (parts[0]) {
      case 'v':
        positions.push([Number(parts[1]), Number(parts[2]), Number(parts[3])]);
        break;
      case 'vn':
        normals.push([Number(parts[1]), Number(parts[2]), Number(parts[3])]);
        break;
      case 'f': {
        // Triangulate any polygon as a fan; BodyParts3D ships triangles.
        const corners = parts.slice(1).map((corner) => {
          const [vertex, , normal] = corner.split('/');
          return { v: Number(vertex) - 1, n: normal ? Number(normal) - 1 : null };
        });
        for (let i = 1; i + 1 < corners.length; i++) {
          indices.push(corners[0], corners[i], corners[i + 1]);
        }
        break;
      }
      default:
        break;
    }
  }

  return { positions, normals, indices, representationId, fileId, licenseNotice };
}

// ---------------------------------------------------------------- mesh assembly

/** One canonical mesh, possibly assembled from several element files. */
function buildMesh(target, members) {
  const positions = [];
  const normals = [];
  const indices = [];
  const sources = [];
  let licenseNotice = null;

  for (const elementId of target.elements) {
    const obj = parseObj(members.get(`${elementId}.obj`).toString('utf8'));
    licenseNotice = licenseNotice ?? obj.licenseNotice;

    if (obj.fileId && obj.fileId !== elementId) {
      throw new Error(`${elementId}.obj reports File ID ${obj.fileId}`);
    }
    // Single-element structures must match the representation id from the official parts list.
    if (target.elements.length === 1 && obj.representationId !== target.representation) {
      throw new Error(
        `${target.mesh}: ${elementId}.obj reports representation ${obj.representationId}, ` +
          `expected ${target.representation} (${target.name})`
      );
    }

    const base = positions.length;
    for (const [x, y, z] of obj.positions) positions.push(transformPoint(x, y, z));
    for (const [x, y, z] of obj.normals) normals.push(transformNormal(x, y, z));
    // Fall back to flat-shaded zero normals only if the file had none (none do today).
    while (normals.length < positions.length) normals.push([0, 1, 0]);
    for (const corner of obj.indices) indices.push(base + corner.v);

    sources.push({
      elementId,
      representationId: obj.representationId,
      vertices: obj.positions.length,
      triangles: obj.indices.length / 3,
    });
  }

  return { ...target, positions, normals, indices, sources, licenseNotice };
}

function boundsOf(meshes) {
  const min = [Infinity, Infinity, Infinity];
  const max = [-Infinity, -Infinity, -Infinity];
  for (const mesh of meshes) {
    for (const point of mesh.positions) {
      for (let axis = 0; axis < 3; axis++) {
        if (point[axis] < min[axis]) min[axis] = point[axis];
        if (point[axis] > max[axis]) max[axis] = point[axis];
      }
    }
  }
  return { min, max };
}

function centroidOf(mesh) {
  const sum = [0, 0, 0];
  for (const point of mesh.positions) {
    sum[0] += point[0];
    sum[1] += point[1];
    sum[2] += point[2];
  }
  return sum.map((value) => value / mesh.positions.length);
}

/**
 * Verifies anatomical side after transforming, using the sign of each mesh centroid on X.
 *
 * This is the check that must never be "fixed" by mirroring geometry: if it fails, the viewer's
 * framing convention is what changes.
 */
function verifySides(meshes) {
  const report = [];
  for (const mesh of meshes) {
    if (mesh.side === 'M') continue;
    const [x] = centroidOf(mesh);
    const expected = mesh.side === 'R' ? x < 0 : x > 0;
    report.push({ mesh: mesh.mesh, side: mesh.side, centroidX: Number(x.toFixed(4)), ok: expected });
  }

  const failures = report.filter((row) => !row.ok);
  if (failures.length > 0) {
    throw new Error(
      'Anatomical side check failed (anatomical right must land on negative X):\n' +
        failures.map((row) => `  ${row.mesh} side=${row.side} centroidX=${row.centroidX}`).join('\n')
    );
  }
  return report;
}

// ---------------------------------------------------------------- GLB writing

function alignTo4(length) {
  return (4 - (length % 4)) % 4;
}

const MATERIAL_INDEX_BY_SYSTEM = {
  skeletal: 0,
  organ: 1,
  nervous: 2,
  nerve: 3,
};

function materialIndexForSystem(system) {
  return MATERIAL_INDEX_BY_SYSTEM[system] ?? 0;
}

function writeGlb(meshes, { attribution, license, generator }) {
  const chunks = [];
  let byteLength = 0;
  const bufferViews = [];
  const accessors = [];

  const pushView = (data) => {
    const padding = alignTo4(byteLength);
    if (padding > 0) {
      chunks.push(Buffer.alloc(padding));
      byteLength += padding;
    }
    const view = { buffer: 0, byteOffset: byteLength, byteLength: data.byteLength };
    bufferViews.push(view);
    chunks.push(data);
    byteLength += data.byteLength;
    return bufferViews.length - 1;
  };

  const gltfMeshes = [];
  const nodes = [];

  for (const mesh of meshes) {
    const vertexCount = mesh.positions.length;
    const positions = new Float32Array(vertexCount * 3);
    const normals = new Float32Array(vertexCount * 3);
    const min = [Infinity, Infinity, Infinity];
    const max = [-Infinity, -Infinity, -Infinity];

    for (let i = 0; i < vertexCount; i++) {
      for (let axis = 0; axis < 3; axis++) {
        const value = mesh.positions[i][axis];
        positions[i * 3 + axis] = value;
        normals[i * 3 + axis] = mesh.normals[i][axis];
        if (value < min[axis]) min[axis] = value;
        if (value > max[axis]) max[axis] = value;
      }
    }

    const useUint16 = vertexCount <= 65535;
    const indexData = useUint16
      ? new Uint16Array(mesh.indices)
      : new Uint32Array(mesh.indices);

    const positionView = pushView(Buffer.from(positions.buffer, 0, positions.byteLength));
    const normalView = pushView(Buffer.from(normals.buffer, 0, normals.byteLength));
    const indexView = pushView(Buffer.from(indexData.buffer, 0, indexData.byteLength));

    const positionAccessor = accessors.push({
      bufferView: positionView,
      componentType: 5126,
      count: vertexCount,
      type: 'VEC3',
      min,
      max,
    }) - 1;
    const normalAccessor = accessors.push({
      bufferView: normalView,
      componentType: 5126,
      count: vertexCount,
      type: 'VEC3',
    }) - 1;
    const indexAccessor = accessors.push({
      bufferView: indexView,
      componentType: useUint16 ? 5123 : 5125,
      count: mesh.indices.length,
      type: 'SCALAR',
    }) - 1;

    const meshIndex = gltfMeshes.push({
      name: mesh.mesh,
      primitives: [
        {
          attributes: { POSITION: positionAccessor, NORMAL: normalAccessor },
          indices: indexAccessor,
          material: materialIndexForSystem(mesh.system),
          mode: 4,
        },
      ],
    }) - 1;

    // The node carries the canonical name too: three.js names the Mesh from the node.
    nodes.push({ name: mesh.mesh, mesh: meshIndex });
  }

  const gltf = {
    asset: {
      version: '2.0',
      generator,
      copyright: attribution,
      extras: { license, attribution },
    },
    scene: 0,
    scenes: [{ name: 'BodyParts3D_Skeleton_V1', nodes: nodes.map((_, index) => index) }],
    nodes,
    meshes: gltfMeshes,
    // Neutral bone material, plus visually distinct non-skeletal materials selected by
    // `mesh.system`. The viewer clones whichever one applies per mesh at load time before
    // highlighting.
    materials: [
      {
        name: 'Bone',
        pbrMetallicRoughness: {
          baseColorFactor: [0.847, 0.839, 0.804, 1],
          metallicFactor: 0.02,
          roughnessFactor: 0.62,
        },
        doubleSided: true,
      },
      {
        name: 'Organ',
        pbrMetallicRoughness: {
          baseColorFactor: [0.694, 0.298, 0.298, 1],
          metallicFactor: 0.0,
          roughnessFactor: 0.75,
        },
        doubleSided: true,
      },
      {
        name: 'NervousTissue',
        pbrMetallicRoughness: {
          baseColorFactor: [0.776, 0.42, 0.549, 1],
          metallicFactor: 0.0,
          roughnessFactor: 0.72,
        },
        doubleSided: true,
      },
      {
        name: 'Nerve',
        pbrMetallicRoughness: {
          baseColorFactor: [0.945, 0.769, 0.259, 1],
          metallicFactor: 0.0,
          roughnessFactor: 0.7,
        },
        doubleSided: true,
      },
    ],
    accessors,
    bufferViews,
    buffers: [{ byteLength }],
  };

  const binPadding = alignTo4(byteLength);
  if (binPadding > 0) {
    chunks.push(Buffer.alloc(binPadding));
    gltf.buffers[0].byteLength = byteLength;
  }
  const bin = Buffer.concat(chunks);

  const jsonBuffer = Buffer.from(JSON.stringify(gltf), 'utf8');
  const jsonPadded = Buffer.concat([jsonBuffer, Buffer.alloc(alignTo4(jsonBuffer.length), 0x20)]);

  const header = Buffer.alloc(12);
  header.write('glTF', 0, 'ascii');
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(12 + 8 + jsonPadded.length + 8 + bin.length, 8);

  const jsonHeader = Buffer.alloc(8);
  jsonHeader.writeUInt32LE(jsonPadded.length, 0);
  jsonHeader.writeUInt32LE(0x4e4f534a, 4);

  const binHeader = Buffer.alloc(8);
  binHeader.writeUInt32LE(bin.length, 0);
  binHeader.writeUInt32LE(0x004e4942, 4);

  return Buffer.concat([header, jsonHeader, jsonPadded, binHeader, bin]);
}

// ---------------------------------------------------------------- main

function parseArgs(argv) {
  const options = { ...DEFAULTS, check: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--archive' || arg === '--partof-archive') options.partofArchive = resolve(argv[++i]);
    else if (arg === '--isa-archive') options.isaArchive = resolve(argv[++i]);
    else if (arg === '--out') options.out = resolve(argv[++i]);
    else if (arg === '--metadata') options.metadata = resolve(argv[++i]);
    else if (arg === '--check') options.check = true;
    else throw new Error(`Unknown argument ${arg}`);
  }
  return options;
}

function sourceOf(target) {
  return target.source ?? 'partof';
}

function archiveSpecs(spec, options) {
  return {
    partof: {
      archive: spec.dataset.archives?.partof?.archive ?? spec.dataset.archive,
      archiveUrl: spec.dataset.archives?.partof?.archiveUrl ?? spec.dataset.archiveUrl,
      archiveSha256: spec.dataset.archives?.partof?.archiveSha256 ?? spec.dataset.archiveSha256,
      path: options.partofArchive,
    },
    isa: {
      archive: spec.dataset.archives?.isa?.archive ?? 'isa_BP3D_4.0_obj_99.zip',
      archiveUrl:
        spec.dataset.archives?.isa?.archiveUrl ??
        'https://dbarchive.biosciencedbc.jp/data/bodyparts3d/LATEST/isa_BP3D_4.0_obj_99.zip',
      archiveSha256: spec.dataset.archives?.isa?.archiveSha256,
      path: options.isaArchive,
    },
  };
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const spec = JSON.parse(readFileSync(resolve(HERE, 'bodyparts3d-elements.json'), 'utf8'));
  const archives = archiveSpecs(spec, options);
  const wantedBySource = new Map();

  for (const target of spec.targets) {
    const source = sourceOf(target);
    if (!archives[source]) {
      throw new Error(`${target.mesh} references unknown source archive "${source}"`);
    }
    if (!wantedBySource.has(source)) wantedBySource.set(source, new Set());
    for (const elementId of target.elements) wantedBySource.get(source).add(`${elementId}.obj`);
  }

  const membersBySource = new Map();
  const archiveHashes = {};
  for (const [source, wanted] of wantedBySource) {
    const archiveSpec = archives[source];
    let archive;
    try {
      archive = readFileSync(archiveSpec.path);
    } catch {
      console.error(
        [
          `Source archive not found for ${source}: ${archiveSpec.path}`,
          '',
          'Download it from the official BodyParts3D / LSDB Archive (CC BY 4.0):',
          `  curl -L -O ${archiveSpec.archiveUrl}`,
          archiveSpec.archiveSha256 ? `Expected sha256: ${archiveSpec.archiveSha256}` : null,
          '',
          'Keep it outside frontend/public — see docs/ANATOMY-3D-ASSET.md.',
        ].filter(Boolean).join('\n')
      );
      process.exit(2);
    }

    const archiveSha = createHash('sha256').update(archive).digest('hex');
    archiveHashes[source] = archiveSha;
    if (archiveSpec.archiveSha256 && archiveSha !== archiveSpec.archiveSha256) {
      console.warn(
        `WARNING: ${source} archive sha256 ${archiveSha} does not match the recorded ` +
          `${archiveSpec.archiveSha256}. The dataset may have been re-released; re-verify the ` +
          'license, the element ids and this build before shipping the result.'
      );
    }
    membersBySource.set(source, readZipMembers(archiveSpec.path, wanted));
  }

  const wantedCount = [...wantedBySource.values()].reduce((total, wanted) => total + wanted.size, 0);
  const meshes = spec.targets.map((target) => buildMesh(target, membersBySource.get(sourceOf(target))));

  // Centre the assembled skeleton on the origin; the viewer's default camera looks at (0, 0, 0).
  const rawBounds = boundsOf(meshes);
  const centre = rawBounds.min.map((min, axis) => (min + rawBounds.max[axis]) / 2);
  for (const mesh of meshes) {
    for (const point of mesh.positions) {
      point[0] -= centre[0];
      point[1] -= centre[1];
      point[2] -= centre[2];
    }
  }

  const sideReport = verifySides(meshes);
  const bounds = boundsOf(meshes);
  const triangles = meshes.reduce((total, mesh) => total + mesh.indices.length / 3, 0);
  const vertices = meshes.reduce((total, mesh) => total + mesh.positions.length, 0);

  const duplicates = meshes
    .map((mesh) => mesh.mesh)
    .filter((name, index, all) => all.indexOf(name) !== index);
  if (duplicates.length > 0) {
    throw new Error(`Duplicate canonical mesh names: ${duplicates.join(', ')}`);
  }

  const attribution =
    'BodyParts3D, © The Database Center for Life Science licensed under CC Attribution 4.0 International';
  const glb = writeGlb(meshes, {
    attribution,
    license: 'CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)',
    generator: 'med-ai scripts/anatomy/build-bodyparts3d-skeleton.mjs',
  });

  const metadata = {
    $comment:
      'Generated by scripts/anatomy/build-bodyparts3d-skeleton.mjs. Do not edit by hand. Frontend ' +
      'tests assert the anatomy manifest against the mesh names recorded here.',
    dataset: spec.dataset,
    license: 'CC BY 4.0',
    licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
    attribution,
    modelPath: '/models/anatomy/bodyparts3d-skeleton-v1.glb',
    builtFromArchiveSha256: archiveHashes.partof ?? Object.values(archiveHashes)[0],
    builtFromArchivesSha256: archiveHashes,
    transform: {
      description:
        'Proper rotation of +90 degrees about X (x, z, -y): source +X = anatomical left, ' +
        '+Y = posterior, +Z = superior, millimetres. Then a uniform 0.001 scale and a translation ' +
        'centring the bounding box. No mirroring.',
      millimetresToUnits: MM_TO_UNITS,
      anatomicalRightAxisSign: -1,
      upAxis: 'Y',
      facingAxis: 'Z',
    },
    bounds,
    meshCount: meshes.length,
    triangleCount: triangles,
    vertexCount: vertices,
    glbByteLength: glb.length,
    sideVerification: sideReport,
    meshes: meshes.map((mesh) => ({
      mesh: mesh.mesh,
      fma: mesh.fma,
      name: mesh.name,
      representation: mesh.representation,
      source: sourceOf(mesh),
      side: mesh.side,
      role: mesh.role,
      system: mesh.system,
      elements: mesh.elements,
      vertices: mesh.positions.length,
      triangles: mesh.indices.length / 3,
      centroid: centroidOf(mesh).map((value) => Number(value.toFixed(4))),
    })),
    sourceLicenseNoticeInObjHeaders: meshes[0]?.licenseNotice ?? null,
  };

  if (options.check) {
    console.log(JSON.stringify({ ...metadata, meshes: metadata.meshes.length }, null, 2));
    return;
  }

  mkdirSync(dirname(options.out), { recursive: true });
  writeFileSync(options.out, glb);
  metadata.glbSha256 = createHash('sha256').update(glb).digest('hex');
  mkdirSync(dirname(options.metadata), { recursive: true });
  writeFileSync(options.metadata, `${JSON.stringify(metadata, null, 2)}\n`);

  const height = (bounds.max[1] - bounds.min[1]).toFixed(3);
  console.log(`BodyParts3D 4.0 (PART-OF/ISA, 99% reduced) -> ${options.out}`);
  for (const [source] of wantedBySource) {
    console.log(`  archive ${source.padEnd(7)} ${archives[source].path}`);
    console.log(`  sha256  ${source.padEnd(7)} ${archiveHashes[source]}`);
  }
  console.log(`  element files    ${wantedCount}`);
  console.log(`  meshes           ${meshes.length} (${meshes.filter((m) => m.role === 'target').length} targetable)`);
  console.log(`  triangles        ${triangles.toLocaleString('en-US')}`);
  console.log(`  vertices         ${vertices.toLocaleString('en-US')}`);
  console.log(`  model height     ${height} units (Y up)`);
  console.log(`  GLB size         ${(glb.length / 1e6).toFixed(2)} MB`);
  console.log(`  GLB sha256       ${metadata.glbSha256}`);
  console.log(`  metadata         ${options.metadata}`);
  console.log(`  side check       ${sideReport.length} sided meshes verified on X`);
}

main();
