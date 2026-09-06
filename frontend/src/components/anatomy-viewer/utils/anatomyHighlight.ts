import { Mesh, MeshStandardMaterial, type Material, type Object3D } from 'three';
import { normalizeMeshName } from './anatomyMeshResolver';

export const HIGHLIGHT_COLOR = 0x60a5fa;
export const BASE_COLOR = 0xd8dee9;
// Kept high so the rest of the skeleton stays clearly visible around the highlighted structure —
// only "Focus on Structure" (ISOLATED_OPACITY) fades it further, on request.
export const SECONDARY_OPACITY = 0.88;
export const ISOLATED_OPACITY = 0.06;

interface HighlightState {
  /** Normalised names of the meshes to highlight. Empty means "no selection". */
  selectedMeshNames: Set<string>;
  /** Selected structure fully visible, everything else strongly faded. */
  isolated: boolean;
}

/**
 * Gives every mesh under `root` its own material instance.
 *
 * Assets routinely share one material across many bones, so mutating it to highlight the right
 * humerus would also light up the left. Cloning once at load keeps highlighting local and leaves
 * the asset's original materials untouched.
 *
 * Returns the clones so the caller can dispose them.
 */
export function isolateMeshMaterials(root: Object3D): Material[] {
  const clones: Material[] = [];

  root.traverse((object) => {
    if (!(object instanceof Mesh)) return;
    if (Array.isArray(object.material)) {
      const cloned = object.material.map((material) => material.clone());
      cloned.forEach(recordBaseColor);
      clones.push(...cloned);
      object.material = cloned;
      return;
    }
    const cloned = object.material.clone();
    recordBaseColor(cloned);
    clones.push(cloned);
    object.material = cloned;
  });

  return clones;
}

/**
 * Applies selection styling to an already-cloned object graph.
 *
 * Unselected bones stay visible but visually secondary — context matters for review — unless the
 * user explicitly isolates the selected structure.
 */
export function applyAnatomyHighlight(root: Object3D, state: HighlightState): void {
  const hasSelection = state.selectedMeshNames.size > 0;

  root.traverse((object) => {
    if (!(object instanceof Mesh)) return;

    const isSelected =
      hasSelection && state.selectedMeshNames.has(normalizeMeshName(object.name));
    const opacity = isSelected || !hasSelection
      ? 1
      : state.isolated
      ? ISOLATED_OPACITY
      : SECONDARY_OPACITY;

    object.visible = true;
    for (const material of materialsOf(object)) {
      if (!(material instanceof MeshStandardMaterial)) continue;
      const baseColor = typeof material.userData.baseColorHex === 'number'
        ? material.userData.baseColorHex
        : BASE_COLOR;
      material.color.set(isSelected ? HIGHLIGHT_COLOR : baseColor);
      material.emissive.set(isSelected ? HIGHLIGHT_COLOR : 0x000000);
      material.emissiveIntensity = isSelected ? 0.55 : 0;
      material.transparent = opacity < 1;
      material.opacity = opacity;
      material.depthWrite = opacity === 1;
      material.needsUpdate = true;
    }
  });
}

function materialsOf(mesh: Mesh): Material[] {
  return Array.isArray(mesh.material) ? mesh.material : [mesh.material];
}

function recordBaseColor(material: Material): void {
  if (!(material instanceof MeshStandardMaterial)) return;
  material.userData.baseColorHex ??= material.color.getHex();
}
