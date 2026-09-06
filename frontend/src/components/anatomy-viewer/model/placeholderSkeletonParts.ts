/**
 * Development placeholder skeleton, built from primitives.
 *
 * The production viewer defaults to the bundled BodyParts3D-derived GLB. This stand-in exists only
 * for development fallback or asset-load failures, so the whole viewer path — manifest lookup,
 * per-mesh material cloning, highlighting, isolate and camera focus — can still be exercised using
 * the same mesh names the real asset exposes.
 *
 * It is anatomically schematic, not anatomically accurate, and the UI says so.
 *
 * Coordinates: a front-facing body roughly 1.8 units tall, centred on the origin. Anatomical right
 * is negative X, matching how the real asset should be oriented.
 */

export type PlaceholderShape =
  | { kind: 'box'; size: [number, number, number] }
  | { kind: 'sphere'; radius: number }
  | { kind: 'cylinder'; radius: number; height: number };

export interface PlaceholderPart {
  /** Must match a mesh name used by the manifest wherever the structure is supported. */
  name: string;
  position: [number, number, number];
  shape: PlaceholderShape;
}

/** Anatomical right is screen-left for a front-facing model. */
const RIGHT = -1;
const LEFT = 1;

function limb(sideSign: number, suffix: string): PlaceholderPart[] {
  const x = (offset: number) => sideSign * offset;

  return [
    { name: `Clavicle_${suffix}`, position: [x(0.11), 0.6, 0.05], shape: { kind: 'box', size: [0.18, 0.03, 0.03] } },
    { name: `Scapula_${suffix}`, position: [x(0.19), 0.5, -0.07], shape: { kind: 'box', size: [0.13, 0.17, 0.03] } },
    { name: `Humerus_${suffix}`, position: [x(0.25), 0.38, 0], shape: { kind: 'cylinder', radius: 0.032, height: 0.34 } },
    { name: `Radius_${suffix}`, position: [x(0.235), 0.08, 0.02], shape: { kind: 'cylinder', radius: 0.022, height: 0.28 } },
    { name: `Ulna_${suffix}`, position: [x(0.275), 0.08, -0.02], shape: { kind: 'cylinder', radius: 0.02, height: 0.28 } },
    { name: `Femur_${suffix}`, position: [x(0.1), -0.2, 0], shape: { kind: 'cylinder', radius: 0.038, height: 0.44 } },
    { name: `Patella_${suffix}`, position: [x(0.1), -0.44, 0.045], shape: { kind: 'sphere', radius: 0.035 } },
    { name: `Tibia_${suffix}`, position: [x(0.095), -0.66, 0], shape: { kind: 'cylinder', radius: 0.032, height: 0.38 } },
    { name: `Fibula_${suffix}`, position: [x(0.145), -0.66, 0], shape: { kind: 'cylinder', radius: 0.018, height: 0.36 } },
    { name: `Talus_${suffix}`, position: [x(0.1), -0.87, 0.01], shape: { kind: 'sphere', radius: 0.038 } },
    { name: `Calcaneus_${suffix}`, position: [x(0.1), -0.9, 0.06], shape: { kind: 'box', size: [0.08, 0.05, 0.17] } },
  ];
}

export const placeholderSkeletonParts: PlaceholderPart[] = [
  { name: 'Skull', position: [0, 0.79, 0], shape: { kind: 'sphere', radius: 0.105 } },
  { name: 'CerebralHemisphere_R', position: [RIGHT * 0.04, 0.8, 0.01], shape: { kind: 'sphere', radius: 0.065 } },
  { name: 'CerebralHemisphere_L', position: [LEFT * 0.04, 0.8, 0.01], shape: { kind: 'sphere', radius: 0.065 } },
  { name: 'Cerebellum', position: [0, 0.74, -0.055], shape: { kind: 'sphere', radius: 0.045 } },
  { name: 'Midbrain', position: [0, 0.75, -0.005], shape: { kind: 'sphere', radius: 0.028 } },
  { name: 'Pons', position: [0, 0.72, -0.015], shape: { kind: 'sphere', radius: 0.03 } },
  { name: 'Medulla', position: [0, 0.69, -0.02], shape: { kind: 'cylinder', radius: 0.018, height: 0.06 } },
  { name: 'Hypothalamus', position: [0, 0.765, 0.025], shape: { kind: 'sphere', radius: 0.018 } },
  { name: 'Epithalamus', position: [0, 0.785, -0.015], shape: { kind: 'sphere', radius: 0.016 } },
  { name: 'ThirdVentricle', position: [0, 0.78, 0.005], shape: { kind: 'sphere', radius: 0.014 } },
  { name: 'FourthVentricle', position: [0, 0.72, -0.045], shape: { kind: 'sphere', radius: 0.014 } },
  { name: 'CentralCanalOfSpinalCord', position: [0, 0.65, -0.02], shape: { kind: 'cylinder', radius: 0.006, height: 0.11 } },
  { name: 'OpticNerve_R', position: [RIGHT * 0.035, 0.765, 0.075], shape: { kind: 'cylinder', radius: 0.006, height: 0.08 } },
  { name: 'OpticNerve_L', position: [LEFT * 0.035, 0.765, 0.075], shape: { kind: 'cylinder', radius: 0.006, height: 0.08 } },
  { name: 'TrochlearNerve_R', position: [RIGHT * 0.055, 0.75, 0.04], shape: { kind: 'cylinder', radius: 0.004, height: 0.08 } },
  { name: 'TrochlearNerve_L', position: [LEFT * 0.055, 0.75, 0.04], shape: { kind: 'cylinder', radius: 0.004, height: 0.08 } },
  { name: 'CranialNerveBranches', position: [0, 0.735, 0.04], shape: { kind: 'box', size: [0.16, 0.08, 0.05] } },
  { name: 'Spine', position: [0, 0.4, -0.02], shape: { kind: 'box', size: [0.06, 0.56, 0.06] } },
  { name: 'Ribcage', position: [0, 0.44, 0.02], shape: { kind: 'box', size: [0.3, 0.34, 0.18] } },
  { name: 'Pelvis', position: [0, 0.04, 0], shape: { kind: 'box', size: [0.28, 0.14, 0.15] } },
  { name: 'Lung_R', position: [RIGHT * 0.09, 0.46, 0.02], shape: { kind: 'box', size: [0.11, 0.24, 0.13] } },
  { name: 'Lung_L', position: [LEFT * 0.09, 0.46, 0.02], shape: { kind: 'box', size: [0.11, 0.24, 0.13] } },
  { name: 'Kidney_R', position: [RIGHT * 0.08, 0.16, -0.06], shape: { kind: 'sphere', radius: 0.045 } },
  { name: 'Kidney_L', position: [LEFT * 0.08, 0.16, -0.06], shape: { kind: 'sphere', radius: 0.045 } },
  ...limb(RIGHT, 'R'),
  ...limb(LEFT, 'L'),
];
