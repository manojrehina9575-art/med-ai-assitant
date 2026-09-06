import { skeletonModelMetadata } from '../model/anatomyViewerManifest';

/**
 * Turns a raw asset mesh name (e.g. "HipBone_R") into a human-readable label for the 3D
 * click-to-explore flow. This is presentation only — it is never a substitute for a backend-mapped
 * `AnatomySelection` and must never be sent anywhere as if it were a verified finding.
 */
export interface ExploredStructure {
  meshName: string;
  displayName: string;
  side: 'LEFT' | 'RIGHT' | 'MIDLINE';
  system: string;
  role: string;
  fma?: string;
  representation?: string;
  source?: string;
  elementCount?: number;
}

const SIDE_SUFFIX = /^(.+)_(L|R)$/i;
const SYSTEM_LABELS: Record<string, string> = {
  skeletal: 'Skeletal',
  organ: 'Organ',
  nervous: 'Nervous Tissue',
  nerve: 'Nerve',
};

const ROLE_LABELS: Record<string, string> = {
  target: 'Targetable',
  context: 'Context',
};

interface MeshMetadata {
  mesh: string;
  name?: string;
  side?: 'R' | 'L' | 'M';
  system?: string;
  role?: string;
  fma?: string;
  representation?: string;
  source?: string;
  elements?: string[];
}

const metadataByMeshName = new Map(
  (skeletonModelMetadata.meshes as MeshMetadata[]).map((mesh) => [normalizeName(mesh.mesh), mesh])
);

export function describeMeshName(meshName: string): ExploredStructure {
  const trimmed = meshName.trim();
  const metadata = metadataByMeshName.get(normalizeName(trimmed));
  const fallback = fallbackDescription(trimmed);

  return {
    meshName: trimmed,
    displayName: metadata?.name ? titleCase(metadata.name) : fallback.displayName,
    side: sideLabel(metadata?.side) ?? fallback.side,
    system: metadata?.system ? SYSTEM_LABELS[metadata.system] ?? titleCase(metadata.system) : 'Unknown',
    role: metadata?.role ? ROLE_LABELS[metadata.role] ?? titleCase(metadata.role) : 'Exploration',
    fma: metadata?.fma,
    representation: metadata?.representation,
    source: metadata?.source ? metadata.source.toUpperCase() : undefined,
    elementCount: metadata?.elements?.length,
  };
}

function fallbackDescription(meshName: string): Pick<ExploredStructure, 'displayName' | 'side'> {
  const match = meshName.match(SIDE_SUFFIX);
  const base = match ? match[1] : meshName;
  const side: ExploredStructure['side'] = match
    ? match[2].toUpperCase() === 'L'
      ? 'LEFT'
      : 'RIGHT'
    : 'MIDLINE';

  return {
    displayName: splitName(base) || meshName,
    side,
  };
}

function sideLabel(side?: 'R' | 'L' | 'M'): ExploredStructure['side'] | null {
  if (side === 'R') return 'RIGHT';
  if (side === 'L') return 'LEFT';
  if (side === 'M') return 'MIDLINE';
  return null;
}

function splitName(name: string): string {
  return name
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim();
}

function titleCase(value: string): string {
  return splitName(value)
    .toLowerCase()
    .replace(/\b[a-z]/g, (letter) => letter.toUpperCase());
}

function normalizeName(name: string): string {
  return name.toLowerCase().replace(/[\s_\-.]/g, '');
}
