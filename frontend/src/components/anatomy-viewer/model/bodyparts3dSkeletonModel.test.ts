import { describe, expect, it, vi } from 'vitest';
import {
  anatomyViewerManifest,
  bundledSkeletonMeshNames,
  skeletonModelAttribution,
  skeletonModelLicense,
  skeletonModelLicenseUrl,
  skeletonModelMetadata,
  skeletonModelUrl,
  usingBundledSkeletonModel,
} from './anatomyViewerManifest';
import { resolveViewerTarget } from '../utils/anatomyMeshResolver';
import { validateAnatomyMeshNames, warnOnAnatomyModelProblems } from '../utils/anatomyModelValidation';

const SUPPORTED_VIEWER_KEYS = [
  'skeleton.humerus.right',
  'skeleton.humerus.left',
  'skeleton.femur.right',
  'skeleton.femur.left',
  'skeleton.shoulder.right',
  'skeleton.shoulder.left',
  'skeleton.knee.right',
  'skeleton.knee.left',
  'skeleton.ankle.right',
  'skeleton.ankle.left',
  'nervous.brain',
  'respiratory.lung.right',
  'respiratory.lung.left',
  'urinary.kidney.right',
  'urinary.kidney.left',
];

describe('bundled BodyParts3D skeleton model', () => {
  it('is configured as the production model with its verified license and attribution', () => {
    expect(skeletonModelUrl).toBe('/models/anatomy/bodyparts3d-skeleton-v1.glb');
    expect(usingBundledSkeletonModel).toBe(true);
    expect(skeletonModelAttribution).toBe(
      'BodyParts3D, © The Database Center for Life Science licensed under CC Attribution 4.0 International'
    );
    expect(skeletonModelLicense).toContain('CC BY 4.0');
    expect(skeletonModelLicenseUrl).toBe('https://creativecommons.org/licenses/by/4.0/');
    expect(skeletonModelMetadata.dataset.name).toBe('BodyParts3D');
    expect(skeletonModelMetadata.dataset.release).toBe('4.0');
  });

  it('records the provenance of every mesh it ships', () => {
    expect(skeletonModelMetadata.meshCount).toBe(skeletonModelMetadata.meshes.length);
    expect(skeletonModelMetadata.triangleCount).toBeGreaterThan(0);

    for (const mesh of skeletonModelMetadata.meshes) {
      expect(mesh.fma).toMatch(/^FMA\d+$/);
      expect(mesh.elements.length).toBeGreaterThan(0);
      for (const element of mesh.elements) expect(element).toMatch(/^FJ\d+$/);
      expect(mesh.triangles).toBeGreaterThan(0);
    }
  });

  it('has a mesh for every supported viewer key and no ambiguous names', () => {
    const validation = validateAnatomyMeshNames(bundledSkeletonMeshNames);

    expect(validation.invalidViewerKeys).toEqual([]);
    expect(validation.missingMeshes).toEqual([]);
    expect(validation.duplicateNames).toEqual([]);
    expect(validation.supportedViewerKeys.sort()).toEqual([...SUPPORTED_VIEWER_KEYS].sort());
  });

  it('keeps every manifest mesh name backed by the generated model metadata', () => {
    for (const [viewerKey, entry] of Object.entries(anatomyViewerManifest)) {
      for (const meshName of entry.meshNames) {
        expect(
          bundledSkeletonMeshNames,
          `${viewerKey} references ${meshName}, but the generated GLB metadata does not list it`
        ).toContain(meshName);
      }
    }
  });

  it('maps the right humerus to Humerus_R and the left to Humerus_L in the model', () => {
    const right = resolveViewerTarget('skeleton.humerus.right');
    const left = resolveViewerTarget('skeleton.humerus.left');
    expect(right.supported && left.supported).toBe(true);
    if (!right.supported || !left.supported) return;

    expect(right.meshNames[0]).toBe('Humerus_R');
    expect(left.meshNames[0]).toBe('Humerus_L');
    expect(bundledSkeletonMeshNames).toContain('Humerus_R');
    expect(bundledSkeletonMeshNames).toContain('Humerus_L');
    expect(bundledSkeletonMeshNames).toEqual(expect.arrayContaining([
      'CerebralHemisphere_R',
      'CerebralHemisphere_L',
      'OpticNerve_R',
      'OpticNerve_L',
      'CranialNerveBranches',
    ]));
  });

  it('never resolves right and left to the same model mesh', () => {
    for (const key of SUPPORTED_VIEWER_KEYS.filter((viewerKey) => viewerKey.endsWith('.right'))) {
      const right = resolveViewerTarget(key);
      const left = resolveViewerTarget(key.replace('.right', '.left'));
      expect(right.supported && left.supported).toBe(true);
      if (!right.supported || !left.supported) continue;

      const inModel = (names: string[]) => names.filter((name) => bundledSkeletonMeshNames.includes(name));
      const rightMeshes = inModel(right.meshNames);
      const leftMeshes = inModel(left.meshNames);

      expect(rightMeshes.length).toBeGreaterThan(0);
      expect(leftMeshes.length).toBeGreaterThan(0);
      for (const mesh of rightMeshes) expect(leftMeshes).not.toContain(mesh);
    }
  });

  it.each([
    ['skeleton.shoulder.right', ['Scapula_R', 'Clavicle_R', 'Humerus_R']],
    ['skeleton.shoulder.left', ['Scapula_L', 'Clavicle_L', 'Humerus_L']],
    ['skeleton.knee.right', ['Femur_R', 'Tibia_R', 'Patella_R']],
    ['skeleton.knee.left', ['Femur_L', 'Tibia_L', 'Patella_L']],
    ['skeleton.ankle.right', ['Tibia_R', 'Fibula_R', 'Talus_R']],
    ['skeleton.ankle.left', ['Tibia_L', 'Fibula_L', 'Talus_L']],
  ])('ships every joint mesh %s needs', (viewerKey, expectedMeshes) => {
    const target = resolveViewerTarget(viewerKey);
    expect(target.supported).toBe(true);
    if (!target.supported) return;

    for (const mesh of expectedMeshes) {
      expect(target.meshNames).toContain(mesh);
      expect(bundledSkeletonMeshNames).toContain(mesh);
    }
    expect(expectedMeshes.length).toBeGreaterThan(1);
  });

  it('verified anatomical side from the geometry at build time', () => {
    const sided = skeletonModelMetadata.sideVerification;
    expect(sided.length).toBeGreaterThanOrEqual(24);
    expect(sided.every((row) => row.ok)).toBe(true);

    // Anatomical right sits on negative X, which is what the camera framing assumes.
    expect(skeletonModelMetadata.transform.anatomicalRightAxisSign).toBe(-1);
    expect(skeletonModelMetadata.transform.upAxis).toBe('Y');

    const centroidOf = (name: string) =>
      skeletonModelMetadata.meshes.find((mesh) => mesh.mesh === name)?.centroid ?? [];
    for (const bone of [
      'Humerus', 'Femur', 'Scapula', 'Clavicle', 'Tibia', 'Fibula', 'Patella', 'Talus', 'Lung', 'Kidney',
    ]) {
      expect(centroidOf(`${bone}_R`)[0]).toBeLessThan(0);
      expect(centroidOf(`${bone}_L`)[0]).toBeGreaterThan(0);
    }
    expect(centroidOf('CerebralHemisphere_R')[0]).toBeLessThan(0);
    expect(centroidOf('CerebralHemisphere_L')[0]).toBeGreaterThan(0);
    expect(centroidOf('OpticNerve_R')[0]).toBeLessThan(0);
    expect(centroidOf('OpticNerve_L')[0]).toBeGreaterThan(0);
  });

  it('covers exactly the viewer keys the manifest declares', () => {
    expect(Object.keys(anatomyViewerManifest).sort()).toEqual([...SUPPORTED_VIEWER_KEYS].sort());
  });
});

describe('validateAnatomyMeshNames', () => {
  it('reports a missing mesh safely instead of throwing', () => {
    const withoutHumeri = bundledSkeletonMeshNames.filter((name) => !name.startsWith('Humerus'));

    const validation = validateAnatomyMeshNames(withoutHumeri);

    expect(validation.invalidViewerKeys).toEqual(['skeleton.humerus.right', 'skeleton.humerus.left']);
    expect(validation.missingMeshes.map((entry) => entry.meshName)).toContain('Humerus_R');
    expect(validation.missingMeshes).toContainEqual({
      viewerKey: 'skeleton.shoulder.right',
      meshName: 'Humerus_R',
    });
    // The shoulder still resolves: the scapula and clavicle are present.
    expect(validation.supportedViewerKeys).toContain('skeleton.shoulder.right');
    expect(validation.duplicateNames).toEqual([]);
  });

  it('warns in development diagnostics when a manifest mesh is missing', () => {
    const withoutHumeri = bundledSkeletonMeshNames.filter((name) => !name.startsWith('Humerus'));
    const validation = validateAnatomyMeshNames(withoutHumeri);
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});

    warnOnAnatomyModelProblems(validation, '/models/anatomy/bodyparts3d-skeleton-v1.glb');

    expect(warn).toHaveBeenCalledWith(expect.stringContaining('missing 4 manifest mesh reference'));
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('skeleton.shoulder.right -> Humerus_R'));
    warn.mockRestore();
  });

  it('detects duplicate mesh names, including cosmetic spelling variants', () => {
    const validation = validateAnatomyMeshNames([...bundledSkeletonMeshNames, 'humerus_r', 'Femur_L']);

    expect(validation.duplicateNames).toHaveLength(2);
    expect(validation.duplicateNames).toEqual(expect.arrayContaining(['Humerus_R', 'Femur_L']));
    expect(validation.invalidViewerKeys).toEqual([]);
  });

  it('reports an empty model as fully invalid without throwing', () => {
    const validation = validateAnatomyMeshNames([]);

    expect(validation.supportedViewerKeys).toEqual([]);
    expect(validation.invalidViewerKeys).toHaveLength(SUPPORTED_VIEWER_KEYS.length);
    expect(validation.missingMeshes.length).toBeGreaterThan(10);
  });
});
