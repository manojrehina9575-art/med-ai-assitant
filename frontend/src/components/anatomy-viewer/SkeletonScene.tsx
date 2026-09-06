import { Suspense, useRef, useState } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { OrbitControls, useProgress } from '@react-three/drei';
import { Group, MOUSE, TOUCH } from 'three';
import { AnatomyCompass } from './AnatomyCompass';
import { AnatomyModel } from './AnatomyModel';
import { useAnatomyFocus, type OrbitLike } from './hooks/useAnatomyFocus';
import { DEFAULT_CAMERA_POSITION, DEFAULT_CAMERA_TARGET, DEFAULT_FOV } from './utils/anatomyFocus';
import type { AnatomyInteractionMode } from './AnatomyControls';

export interface SkeletonSceneProps {
  highlightedMeshNames: string[];
  selectionKey: string;
  focusRegion: string | null;
  isolated: boolean;
  resetNonce: number;
  focusNonce: number;
  interactionMode: AnatomyInteractionMode;
  onExploreMesh: (meshName: string) => void;
}

const MOVE_MOUSE_BUTTONS = { LEFT: MOUSE.PAN, MIDDLE: MOUSE.DOLLY, RIGHT: MOUSE.ROTATE };
const ROTATE_MOUSE_BUTTONS = { LEFT: MOUSE.ROTATE, MIDDLE: MOUSE.DOLLY, RIGHT: MOUSE.PAN };
const MOVE_TOUCHES = { ONE: TOUCH.PAN, TWO: TOUCH.DOLLY_PAN };
const ROTATE_TOUCHES = { ONE: TOUCH.ROTATE, TWO: TOUCH.DOLLY_PAN };

/**
 * The WebGL surface. Lazily imported by {@link AnatomyViewer} so three.js stays out of the initial
 * bundle and so a failure to load it is caught by the viewer's error boundary.
 *
 * Interaction uses OrbitControls with a viewer mode switch: Move makes left drag pan the anatomy,
 * while Rotate makes left drag orbit it. Wheel/pinch always zoom.
 * Sized to its container so it embeds in the Clinical Workspace rather than taking over the screen.
 */
export default function SkeletonScene({
  highlightedMeshNames,
  selectionKey,
  focusRegion,
  isolated,
  resetNonce,
  focusNonce,
  interactionMode,
  onExploreMesh,
}: SkeletonSceneProps) {
  const { active, progress } = useProgress();
  const [azimuthDegrees, setAzimuthDegrees] = useState(0);

  return (
    <div
      className="relative h-full w-full"
      style={{ background: 'radial-gradient(circle at 50% 38%, #1e293b 0%, #0a0f1e 72%, #060910 100%)' }}
    >
      <Canvas
        camera={{ position: DEFAULT_CAMERA_POSITION, fov: DEFAULT_FOV, near: 0.05, far: 50 }}
        dpr={[1, 2]}
        gl={{ antialias: true, alpha: true }}
        style={{
          background: 'transparent',
          cursor: interactionMode === 'move' ? 'move' : 'grab',
          touchAction: 'none',
        }}
      >
        <ambientLight intensity={0.6} />
        <hemisphereLight args={[0xdbeafe, 0x0f172a, 0.55]} />
        <directionalLight position={[2.5, 3, 4]} intensity={1.15} />
        <directionalLight position={[-3, 1.5, -2]} intensity={0.35} />
        {/* Rim light: separates the skeleton's silhouette from the dark vignette behind it. */}
        <directionalLight position={[0, 1.5, -4]} intensity={0.6} color={0x60a5fa} />

        <Suspense fallback={null}>
          <SceneContents
            highlightedMeshNames={highlightedMeshNames}
            selectionKey={selectionKey}
            focusRegion={focusRegion}
            isolated={isolated}
            resetNonce={resetNonce}
            focusNonce={focusNonce}
            interactionMode={interactionMode}
            onExploreMesh={onExploreMesh}
            onAzimuthChange={setAzimuthDegrees}
          />
        </Suspense>
      </Canvas>

      <AnatomyCompass azimuthDegrees={azimuthDegrees} />

      {active && (
        <div className="pointer-events-none absolute inset-x-0 bottom-2 text-center">
          <span className="rounded-full bg-slate-950/80 px-3 py-1 text-[11px] font-medium text-slate-300">
            Loading anatomy model… {Math.round(progress)}%
          </span>
        </div>
      )}
    </div>
  );
}

function SceneContents({
  highlightedMeshNames,
  selectionKey,
  focusRegion,
  isolated,
  resetNonce,
  focusNonce,
  interactionMode,
  onExploreMesh,
  onAzimuthChange,
}: SkeletonSceneProps & { onAzimuthChange: (azimuthDegrees: number) => void }) {
  const groupRef = useRef<Group>(null!);
  const controlsRef = useRef<OrbitLike | null>(null);

  const stopScriptedCameraMove = useAnatomyFocus({
    root: groupRef,
    controls: controlsRef,
    meshNames: highlightedMeshNames.length > 0 ? highlightedMeshNames : null,
    selectionKey,
    focusRegion,
    resetNonce,
    focusNonce,
  });
  useCameraAzimuth(onAzimuthChange);

  return (
    <>
      <AnatomyModel
        groupRef={groupRef}
        highlightedMeshNames={highlightedMeshNames}
        isolated={isolated}
        onExploreMesh={onExploreMesh}
      />
      <OrbitControls
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ref={controlsRef as any}
        makeDefault
        enableRotate
        enableZoom
        enablePan
        enableDamping
        dampingFactor={0.12}
        minDistance={0.35}
        maxDistance={8}
        screenSpacePanning
        panSpeed={1}
        rotateSpeed={0.75}
        zoomSpeed={0.9}
        mouseButtons={interactionMode === 'move' ? MOVE_MOUSE_BUTTONS : ROTATE_MOUSE_BUTTONS}
        touches={interactionMode === 'move' ? MOVE_TOUCHES : ROTATE_TOUCHES}
        onStart={stopScriptedCameraMove}
      />
    </>
  );
}

/**
 * Reports the camera's azimuth (degrees, 0 at the default front view) to `onChange`, throttled so
 * a per-frame value doesn't drive a React re-render every frame.
 */
function useCameraAzimuth(onChange: (azimuthDegrees: number) => void) {
  const { camera } = useThree();
  const lastReportedAt = useRef(0);
  const lastValue = useRef(0);

  useFrame((state) => {
    const now = state.clock.elapsedTime;
    if (now - lastReportedAt.current < 0.1) return;
    lastReportedAt.current = now;

    const [targetX, , targetZ] = DEFAULT_CAMERA_TARGET;
    const azimuthDegrees =
      (Math.atan2(camera.position.x - targetX, camera.position.z - targetZ) * 180) / Math.PI;

    if (Math.abs(azimuthDegrees - lastValue.current) < 0.5) return;
    lastValue.current = azimuthDegrees;
    onChange(azimuthDegrees);
  });
}
