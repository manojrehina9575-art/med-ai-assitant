import { Box3, Mesh, Vector3, type Object3D } from 'three';
import { normalizeMeshName } from './anatomyMeshResolver';

export const DEFAULT_CAMERA_POSITION: [number, number, number] = [0, 0.15, 3.1];
export const DEFAULT_CAMERA_TARGET: [number, number, number] = [0, 0, 0];
export const DEFAULT_FOV = 42;

export interface FocusFrame {
  target: Vector3;
  cameraPosition: Vector3;
}

/**
 * Bounding box of the named meshes within `root`, or null when none of them are present.
 *
 * Uses the asset's own geometry rather than hard-coded coordinates, so the framing stays correct if
 * the skeleton asset is replaced.
 */
export function boundingBoxOfMeshes(root: Object3D, meshNames: string[]): Box3 | null {
  const wanted = new Set(meshNames.map(normalizeMeshName));
  const box = new Box3();
  let found = false;

  root.traverse((object) => {
    if (!(object instanceof Mesh)) return;
    if (!wanted.has(normalizeMeshName(object.name))) return;
    box.expandByObject(object);
    found = true;
  });

  return found && !box.isEmpty() ? box : null;
}

/**
 * Camera placement that frames a bounding sphere of `radius` around `center`.
 *
 * Right-sided structures are viewed slightly from the patient's right (screen left for a
 * front-facing model) so the selected bone is not hidden behind the torso. This is coarse framing,
 * not a medically precise sub-bone viewpoint.
 */
export function focusFrame(center: Vector3, radius: number, sideSign: number, fov = DEFAULT_FOV): FocusFrame {
  const safeRadius = Math.max(radius, 0.08);
  const halfFovRadians = (fov * Math.PI) / 360;
  const distance = (safeRadius / Math.sin(halfFovRadians)) * 1.45;
  const direction = new Vector3(sideSign * 0.55, 0.18, 1).normalize();

  return {
    target: center.clone(),
    cameraPosition: center.clone().add(direction.multiplyScalar(distance)),
  };
}

/** Anatomical right sits on screen-left for a front-facing model. */
export function sideSignForFocusRegion(focusRegion: string): number {
  if (focusRegion.startsWith('right')) return -1;
  if (focusRegion.startsWith('left')) return 1;
  return 0;
}

/** Framing for a named structure, or null when the asset has none of its meshes. */
export function focusFrameForMeshes(
  root: Object3D,
  meshNames: string[],
  focusRegion: string,
  fov = DEFAULT_FOV
): FocusFrame | null {
  const box = boundingBoxOfMeshes(root, meshNames);
  if (!box) return null;

  const center = box.getCenter(new Vector3());
  const radius = box.getSize(new Vector3()).length() / 2;
  return focusFrame(center, radius, sideSignForFocusRegion(focusRegion), fov);
}
