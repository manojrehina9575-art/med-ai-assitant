import { describe, expect, it } from 'vitest';
import { BoxGeometry, Group, Mesh, MeshStandardMaterial, Vector3 } from 'three';
import { anatomyViewerManifest } from '../model/anatomyViewerManifest';
import { placeholderSkeletonParts } from '../model/placeholderSkeletonParts';
import {
  DEFAULT_CAMERA_POSITION,
  boundingBoxOfMeshes,
  focusFrame,
  focusFrameForMeshes,
  sideSignForFocusRegion,
} from './anatomyFocus';

function placeholderRoot(): Group {
  const root = new Group();
  for (const part of placeholderSkeletonParts) {
    const mesh = new Mesh(new BoxGeometry(0.1, 0.1, 0.1), new MeshStandardMaterial());
    mesh.name = part.name;
    mesh.position.set(...part.position);
    root.add(mesh);
  }
  root.updateMatrixWorld(true);
  return root;
}

describe('anatomy camera focus', () => {
  it('frames the selected structure rather than the whole skeleton', () => {
    const root = placeholderRoot();

    const box = boundingBoxOfMeshes(root, anatomyViewerManifest['skeleton.humerus.right'].meshNames);
    expect(box).not.toBeNull();

    const center = box!.getCenter(new Vector3());
    // Anatomical right sits at negative X in this model space.
    expect(center.x).toBeLessThan(0);
    expect(center.y).toBeGreaterThan(0);
  });

  it('returns null when the asset has none of the target meshes', () => {
    expect(boundingBoxOfMeshes(placeholderRoot(), ['Sternum'])).toBeNull();
    expect(focusFrameForMeshes(placeholderRoot(), ['Sternum'], 'right_upper_arm')).toBeNull();
  });

  it('places the camera off-axis toward the selected side and outside the structure', () => {
    const right = focusFrameForMeshes(
      placeholderRoot(),
      anatomyViewerManifest['skeleton.femur.right'].meshNames,
      'right_thigh'
    );
    const left = focusFrameForMeshes(
      placeholderRoot(),
      anatomyViewerManifest['skeleton.femur.left'].meshNames,
      'left_thigh'
    );

    expect(right).not.toBeNull();
    expect(left).not.toBeNull();
    expect(right!.cameraPosition.x).toBeLessThan(right!.target.x);
    expect(left!.cameraPosition.x).toBeGreaterThan(left!.target.x);
    expect(right!.cameraPosition.z).toBeGreaterThan(right!.target.z);
    expect(right!.cameraPosition.distanceTo(right!.target)).toBeGreaterThan(0.1);
  });

  it('frames a joint target wider than a single bone', () => {
    const root = placeholderRoot();
    const knee = focusFrameForMeshes(root, anatomyViewerManifest['skeleton.knee.right'].meshNames, 'right_knee');
    const patellaOnly = focusFrameForMeshes(root, ['Patella_R'], 'right_knee');

    expect(knee!.cameraPosition.distanceTo(knee!.target)).toBeGreaterThan(
      patellaOnly!.cameraPosition.distanceTo(patellaOnly!.target)
    );
  });

  it('moves the camera further away for larger structures', () => {
    const near = focusFrame(new Vector3(), 0.1, -1);
    const far = focusFrame(new Vector3(), 0.6, -1);

    expect(far.cameraPosition.length()).toBeGreaterThan(near.cameraPosition.length());
  });

  it('keeps a default camera for the whole-skeleton view', () => {
    expect(DEFAULT_CAMERA_POSITION[2]).toBeGreaterThan(1);
    expect(sideSignForFocusRegion('right_knee')).toBe(-1);
    expect(sideSignForFocusRegion('left_knee')).toBe(1);
    expect(sideSignForFocusRegion('head')).toBe(0);
  });
});
