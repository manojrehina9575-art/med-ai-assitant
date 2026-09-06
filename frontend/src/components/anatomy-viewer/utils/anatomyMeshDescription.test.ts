import { describe, expect, it } from 'vitest';
import { describeMeshName } from './anatomyMeshDescription';

describe('describeMeshName', () => {
  it('splits a right-sided bone into a display name and side', () => {
    expect(describeMeshName('Humerus_R')).toEqual(expect.objectContaining({
      meshName: 'Humerus_R',
      displayName: 'Right Humerus',
      side: 'RIGHT',
      system: 'Skeletal',
      role: 'Targetable',
      fma: 'FMA23130',
      source: 'PARTOF',
      elementCount: 1,
    }));
  });

  it('splits a left-sided bone into a display name and side', () => {
    expect(describeMeshName('Femur_L')).toEqual(expect.objectContaining({
      meshName: 'Femur_L',
      displayName: 'Left Femur',
      side: 'LEFT',
      system: 'Skeletal',
    }));
  });

  it('spaces out a multi-word bone name', () => {
    expect(describeMeshName('HipBone_R')).toEqual(expect.objectContaining({
      meshName: 'HipBone_R',
      displayName: 'Right Hip Bone',
      side: 'RIGHT',
      role: 'Context',
    }));
  });

  it('treats a bone with no side suffix as midline', () => {
    expect(describeMeshName('Sacrum')).toEqual(expect.objectContaining({
      meshName: 'Sacrum',
      displayName: 'Sacrum',
      side: 'MIDLINE',
      system: 'Skeletal',
    }));
    expect(describeMeshName('Sternum').side).toBe('MIDLINE');
  });

  it('uses generated metadata for brain and nerve meshes', () => {
    expect(describeMeshName('CerebralHemisphere_R')).toEqual(expect.objectContaining({
      displayName: 'Right Cerebral Hemisphere',
      side: 'RIGHT',
      system: 'Nervous Tissue',
      role: 'Targetable',
    }));
    expect(describeMeshName('OpticNerve_L')).toEqual(expect.objectContaining({
      displayName: 'Left Optic Nerve',
      side: 'LEFT',
      system: 'Nerve',
      role: 'Context',
      source: 'ISA',
    }));
  });

  it('falls back safely for an unknown mesh name', () => {
    expect(describeMeshName('CustomMesh_R')).toEqual({
      meshName: 'CustomMesh_R',
      displayName: 'Custom Mesh',
      side: 'RIGHT',
      system: 'Unknown',
      role: 'Exploration',
      fma: undefined,
      representation: undefined,
      source: undefined,
      elementCount: undefined,
    });
  });
});
