import { useCallback, useEffect, useRef, type RefObject } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import { Vector3, type Object3D } from 'three';
import {
  DEFAULT_CAMERA_POSITION,
  DEFAULT_CAMERA_TARGET,
  focusFrameForMeshes,
  type FocusFrame,
} from '../utils/anatomyFocus';

/** The slice of OrbitControls this hook drives. Avoids depending on drei's control types. */
export interface OrbitLike {
  target: Vector3;
  update: () => void;
}

interface UseAnatomyFocusOptions {
  root: RefObject<Object3D | null>;
  controls: RefObject<OrbitLike | null>;
  /** Meshes to frame when the camera is asked to focus. Null means no renderable selection. */
  meshNames: string[] | null;
  /** Stable clinical target identity. Raw mesh exploration must not reset the camera. */
  selectionKey: string;
  focusRegion: string | null;
  /** Changing this asks for the default whole-body camera again. */
  resetNonce: number;
  /** Changing this asks the camera to zoom to the current selection ("Focus on Structure"). */
  focusNonce: number;
}

const DEFAULT_FRAME: FocusFrame = {
  target: new Vector3(...DEFAULT_CAMERA_TARGET),
  cameraPosition: new Vector3(...DEFAULT_CAMERA_POSITION),
};

/**
 * Moves the camera toward the selected structure on request, or back to the whole-body default.
 *
 * The whole-body view is the resting state: a new selection (or the initial mount) frames the
 * whole skeleton, not the selected structure — the camera only zooms in when the reviewer
 * explicitly asks it to via "Focus on Structure". The transition is a frame-rate independent ease
 * so it never snaps. Focus is derived from the loaded geometry's bounding box, never from
 * hard-coded per-bone coordinates.
 */
export function useAnatomyFocus({
  root,
  controls,
  meshNames,
  selectionKey,
  focusRegion,
  resetNonce,
  focusNonce,
}: UseAnatomyFocusOptions): () => void {
  const { camera } = useThree();
  const desired = useRef<FocusFrame>(DEFAULT_FRAME);
  const movingToFrame = useRef(false);
  const stopScriptedCameraMove = useCallback(() => {
    movingToFrame.current = false;
  }, []);

  // A new clinical selection rests on the whole-body view. Clicking a mesh for visual exploration
  // changes `meshNames`, but leaves `selectionKey` alone, so the camera does not snap back.
  useEffect(() => {
    desired.current = DEFAULT_FRAME;
    movingToFrame.current = true;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectionKey, focusRegion]);

  // "Focus on Structure" zooms the camera in on the current selection.
  useEffect(() => {
    if (focusNonce === 0) return;
    const rootObject = root.current;
    if (!rootObject || !meshNames || meshNames.length === 0 || !focusRegion) return;

    // Geometry may not be attached on the first pass after a lazy load; fall back to the default
    // view rather than framing an empty box.
    desired.current = focusFrameForMeshes(rootObject, meshNames, focusRegion) ?? DEFAULT_FRAME;
    movingToFrame.current = true;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusNonce]);

  // Reset View returns to the whole-body camera even while a structure stays selected, so it must
  // not be folded into the effects above.
  useEffect(() => {
    if (resetNonce === 0) return;
    desired.current = DEFAULT_FRAME;
    movingToFrame.current = true;
  }, [resetNonce]);

  useFrame((_, delta) => {
    if (!movingToFrame.current) return;

    const ease = 1 - Math.pow(0.0001, delta);
    camera.position.lerp(desired.current.cameraPosition, ease);

    const orbit = controls.current;
    if (orbit) {
      orbit.target.lerp(desired.current.target, ease);
      orbit.update();
    } else {
      camera.lookAt(desired.current.target);
    }

    const cameraSettled = camera.position.distanceTo(desired.current.cameraPosition) < 0.002;
    const targetSettled = orbit
      ? orbit.target.distanceTo(desired.current.target) < 0.002
      : true;

    if (cameraSettled && targetSettled) {
      camera.position.copy(desired.current.cameraPosition);
      if (orbit) {
        orbit.target.copy(desired.current.target);
        orbit.update();
      } else {
        camera.lookAt(desired.current.target);
      }
      movingToFrame.current = false;
    }
  });

  return stopScriptedCameraMove;
}
