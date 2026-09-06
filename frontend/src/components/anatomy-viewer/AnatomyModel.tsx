import { useEffect, useMemo } from 'react';
import { useGLTF } from '@react-three/drei';
import type { ThreeEvent } from '@react-three/fiber';
import { Group, type Material, type Object3D } from 'three';
import { skeletonModelUrl } from './model/anatomyViewerManifest';
import { placeholderSkeletonParts } from './model/placeholderSkeletonParts';
import { applyAnatomyHighlight, isolateMeshMaterials } from './utils/anatomyHighlight';
import { normalizedMeshNames } from './utils/anatomyMeshResolver';
import { validateAnatomyModel, warnOnAnatomyModelProblems } from './utils/anatomyModelValidation';

interface AnatomyModelProps {
  /** Root the scene uses for focus framing. */
  groupRef: React.RefObject<Group>;
  /** Mesh names to highlight; empty means no highlight. */
  highlightedMeshNames: string[];
  isolated: boolean;
  onExploreMesh: (meshName: string) => void;
}

/**
 * The skeleton object graph plus highlighting.
 *
 * The model is loaded once (drei caches by URL) — selection, highlight and camera changes operate
 * on the already-loaded scene rather than re-fetching it.
 */
export function AnatomyModel({
  groupRef,
  highlightedMeshNames,
  isolated,
  onExploreMesh,
}: AnatomyModelProps) {
  const selected = useMemo(() => normalizedMeshNames(highlightedMeshNames), [highlightedMeshNames]);

  // Give every mesh its own material before any highlight runs, so styling the right humerus can
  // never bleed into the left through a shared material instance.
  useEffect(() => {
    const root = groupRef.current;
    if (!root) return;

    const clones: Material[] = isolateMeshMaterials(root);

    // Diagnostic only: a manifest entry with no mesh degrades that one selection, and the viewer
    // already reports it honestly, so this must never interrupt review.
    if (import.meta.env.DEV) {
      warnOnAnatomyModelProblems(validateAnatomyModel(root), skeletonModelUrl ?? 'placeholder skeleton');
    }

    return () => clones.forEach((material) => material.dispose());
  }, [groupRef]);

  useEffect(() => {
    const root = groupRef.current;
    if (!root) return;
    applyAnatomyHighlight(root, { selectedMeshNames: selected, isolated });
  }, [groupRef, isolated, selected]);

  function handleClick(event: ThreeEvent<MouseEvent>) {
    // Visual exploration only. Clicking a bone never creates or edits a clinical finding.
    event.stopPropagation();
    const name = (event.object as Object3D).name;
    if (name) onExploreMesh(name);
  }

  return (
    <group ref={groupRef} onClick={handleClick}>
      {skeletonModelUrl ? <LoadedSkeleton url={skeletonModelUrl} /> : <PlaceholderSkeleton />}
    </group>
  );
}

function LoadedSkeleton({ url }: { url: string }) {
  const { scene } = useGLTF(url);
  // Cloning keeps the cached original pristine while this instance gets its own materials.
  const instance = useMemo(() => scene.clone(true), [scene]);
  return <primitive object={instance} />;
}

/** Schematic stand-in used until a licensed skeleton asset is configured. */
function PlaceholderSkeleton() {
  return (
    <group>
      {placeholderSkeletonParts.map((part) => (
        <mesh key={part.name} name={part.name} position={part.position}>
          {part.shape.kind === 'box' && <boxGeometry args={part.shape.size} />}
          {part.shape.kind === 'sphere' && <sphereGeometry args={[part.shape.radius, 20, 16]} />}
          {part.shape.kind === 'cylinder' && (
            <cylinderGeometry args={[part.shape.radius, part.shape.radius, part.shape.height, 16]} />
          )}
          <meshStandardMaterial roughness={0.55} metalness={0.05} />
        </mesh>
      ))}
    </group>
  );
}

if (skeletonModelUrl) {
  useGLTF.preload(skeletonModelUrl);
}
