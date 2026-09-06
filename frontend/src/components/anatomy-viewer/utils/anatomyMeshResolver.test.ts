import { describe, expect, it } from 'vitest';
import { anatomyViewerManifest } from '../model/anatomyViewerManifest';
import { placeholderSkeletonParts } from '../model/placeholderSkeletonParts';
import { normalizeMeshName, resolveViewerTarget } from './anatomyMeshResolver';

describe('resolveViewerTarget', () => {
  it('resolves the right humerus to its own manifest entry', () => {
    const target = resolveViewerTarget('skeleton.humerus.right');

    expect(target.supported).toBe(true);
    if (!target.supported) return;
    expect(target.viewerKey).toBe('skeleton.humerus.right');
    expect(target.meshNames).toContain('Humerus_R');
    expect(target.meshNames.join(' ')).not.toContain('Humerus_L');
    expect(target.focusRegion).toBe('right_upper_arm');
  });

  it('resolves the left humerus separately from the right', () => {
    const left = resolveViewerTarget('skeleton.humerus.left');
    const right = resolveViewerTarget('skeleton.humerus.right');

    expect(left.supported && right.supported).toBe(true);
    if (!left.supported || !right.supported) return;
    expect(left.meshNames).toContain('Humerus_L');
    expect(left.meshNames).not.toEqual(right.meshNames);
    expect(left.focusRegion).toBe('left_upper_arm');
    expect(left.focusRegion).not.toBe(right.focusRegion);
  });

  it('resolves both femurs to distinct meshes', () => {
    const right = resolveViewerTarget('skeleton.femur.right');
    const left = resolveViewerTarget('skeleton.femur.left');

    expect(right.supported && left.supported).toBe(true);
    if (!right.supported || !left.supported) return;
    expect(right.meshNames).toContain('Femur_R');
    expect(left.meshNames).toContain('Femur_L');
    expect(right.meshNames).not.toEqual(left.meshNames);
  });

  it('maps the shoulder to the girdle rather than collapsing it to the humerus', () => {
    const target = resolveViewerTarget('skeleton.shoulder.left');

    expect(target.supported).toBe(true);
    if (!target.supported) return;
    expect(target.meshNames.length).toBeGreaterThan(1);
    expect(target.meshNames).toEqual(expect.arrayContaining(['Scapula_L', 'Clavicle_L']));
    expect(target.coveredStructures).toEqual(expect.arrayContaining(['Scapula', 'Clavicle']));
  });

  it('maps the knee to multiple meshes rather than the femur alone', () => {
    const target = resolveViewerTarget('skeleton.knee.right');

    expect(target.supported).toBe(true);
    if (!target.supported) return;
    expect(target.meshNames).toEqual(expect.arrayContaining(['Femur_R', 'Tibia_R', 'Patella_R']));
    expect(target.meshNames.length).toBeGreaterThan(1);
  });

  it('maps the ankle to multiple meshes rather than the tibia alone', () => {
    const target = resolveViewerTarget('skeleton.ankle.left');

    expect(target.supported).toBe(true);
    if (!target.supported) return;
    expect(target.meshNames).toEqual(expect.arrayContaining(['Tibia_L', 'Fibula_L', 'Talus_L']));
    expect(target.meshNames.length).toBeGreaterThan(1);
  });

  it('resolves lungs and kidneys to their own organ meshes', () => {
    const lung = resolveViewerTarget('respiratory.lung.left');
    const kidney = resolveViewerTarget('urinary.kidney.right');

    expect(lung.supported).toBe(true);
    if (lung.supported) expect(lung.meshNames).toEqual(['Lung_L']);

    expect(kidney.supported).toBe(true);
    if (kidney.supported) expect(kidney.meshNames).toEqual(['Kidney_R']);
  });

  it('resolves the brain to its own nervous-system mesh', () => {
    const brain = resolveViewerTarget('nervous.brain');

    expect(brain.supported).toBe(true);
    if (!brain.supported) return;
    expect(brain.meshNames).toEqual(expect.arrayContaining([
      'CerebralHemisphere_R',
      'CerebralHemisphere_L',
      'Cerebellum',
      'Midbrain',
    ]));
    expect(brain.focusRegion).toBe('head');
    expect(brain.coveredStructures).toEqual(expect.arrayContaining(['Brain', 'Brainstem', 'Cerebellum']));
  });

  it('reports a genuinely unbuilt system as unsupported without inventing a mesh', () => {
    const heart = resolveViewerTarget('cardiac.heart');

    expect(heart.supported).toBe(false);
    if (heart.supported) return;
    expect(heart.reason).toBe('NON_SKELETAL_SYSTEM');
    expect(heart.message).toBe('3D model not available for this structure yet.');
  });

  it('reports a missing viewer key as having no single target', () => {
    for (const value of [null, undefined, '', '   ']) {
      const target = resolveViewerTarget(value);
      expect(target.supported).toBe(false);
      if (target.supported) return;
      expect(target.reason).toBe('NO_VIEWER_KEY');
      expect(target.viewerKey).toBeNull();
      expect(target.message).toBe('No single 3D structure is available for this mapped finding.');
    }
  });

  it('fails safely for an unknown or not-yet-mapped viewer key', () => {
    const unmappedSkeletal = resolveViewerTarget('skeleton.sternum');
    const nonsense = resolveViewerTarget('not-a-viewer-key');

    expect(unmappedSkeletal.supported).toBe(false);
    if (!unmappedSkeletal.supported) {
      expect(unmappedSkeletal.reason).toBe('UNMAPPED_VIEWER_KEY');
      expect(unmappedSkeletal.viewerKey).toBe('skeleton.sternum');
    }
    expect(nonsense.supported).toBe(false);
    if (!nonsense.supported) {
      expect(nonsense.reason).toBe('NON_SKELETAL_SYSTEM');
    }
  });

  it('keeps right and left entries disjoint across every supported structure', () => {
    for (const key of Object.keys(anatomyViewerManifest)) {
      if (!key.endsWith('.right')) continue;
      const right = anatomyViewerManifest[key];
      const left = anatomyViewerManifest[key.replace('.right', '.left')];

      expect(left).toBeDefined();
      const rightNames = new Set(right.meshNames.map(normalizeMeshName));
      for (const name of left.meshNames) {
        expect(rightNames.has(normalizeMeshName(name))).toBe(false);
      }
    }
  });

  it('normalizes mesh names so asset naming variants still match', () => {
    expect(normalizeMeshName('Humerus_R')).toBe(normalizeMeshName('humerus r'));
    expect(normalizeMeshName('Humerus-R')).toBe(normalizeMeshName('HumerusR'));
    expect(normalizeMeshName('Humerus_R')).not.toBe(normalizeMeshName('Humerus_L'));
  });

  it('has a placeholder mesh for every supported viewer key', () => {
    const placeholderNames = new Set(placeholderSkeletonParts.map((part) => normalizeMeshName(part.name)));

    for (const [key, entry] of Object.entries(anatomyViewerManifest)) {
      const present = entry.meshNames.filter((name) => placeholderNames.has(normalizeMeshName(name)));
      expect(present.length, `no placeholder mesh for ${key}`).toBeGreaterThan(0);
    }
  });
});
