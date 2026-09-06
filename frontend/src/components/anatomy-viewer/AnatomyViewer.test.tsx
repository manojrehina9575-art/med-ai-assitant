import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AnatomyViewer } from './AnatomyViewer';
import { AnatomyPreview } from '@/components/clinical-workspace/AnatomyPreview';
import type { SkeletonSceneProps } from './SkeletonScene';
import type { AnatomySelection } from '@/types/clinicalWorkspace';

// WebGL never exists in jsdom; force the supported branch so the viewer's own wiring is testable.
vi.mock('./utils/webgl', () => ({ supportsWebGl: () => true }));

let sceneShouldThrow = false;

// Stands in for the WebGL surface. Records exactly what the viewer hands the scene.
vi.mock('./SkeletonScene', () => ({
  default: (props: SkeletonSceneProps) => {
    if (sceneShouldThrow) throw new Error('simulated GLB failure');
    return (
      <div
        data-testid="skeleton-scene"
        data-highlighted={props.highlightedMeshNames.join(',')}
        data-focus-region={props.focusRegion ?? ''}
        data-selection-key={props.selectionKey}
        data-isolated={String(props.isolated)}
        data-reset-nonce={String(props.resetNonce)}
        data-interaction-mode={props.interactionMode}
      >
        <button type="button" onClick={() => props.onExploreMesh('Femur_L')}>
          simulate mesh click
        </button>
      </div>
    );
  },
}));

function selection(overrides: Partial<AnatomySelection> = {}): AnatomySelection {
  return {
    structure: 'HUMERUS',
    displayName: 'Right proximal humerus',
    side: 'RIGHT',
    region: 'PROXIMAL',
    system: 'Skeletal',
    viewerKey: 'skeleton.humerus.right',
    sourceLabel: 'Findings',
    sourceKind: 'QA',
    ...overrides,
  };
}

describe('AnatomyViewer', () => {
  beforeEach(() => {
    sceneShouldThrow = false;
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('passes the resolved meshes and focus region for the backend viewer key to the scene', async () => {
    render(<AnatomyViewer selection={selection()} />);

    const scene = await screen.findByTestId('skeleton-scene');
    expect(scene.dataset.highlighted).toBe('Humerus_R');
    expect(scene.dataset.focusRegion).toBe('right_upper_arm');
    expect(screen.getByText(/Highlighted structures: Humerus\./)).toBeInTheDocument();
  });

  it('defaults to move mode for panning after zoom and can switch back to rotate mode', async () => {
    const user = userEvent.setup();
    render(<AnatomyViewer selection={selection()} />);

    expect((await screen.findByTestId('skeleton-scene')).dataset.interactionMode).toBe('move');
    expect(screen.getByRole('button', { name: /Move/i })).toHaveAttribute('aria-pressed', 'true');

    await user.click(screen.getByRole('button', { name: /Rotate/i }));

    expect((await screen.findByTestId('skeleton-scene')).dataset.interactionMode).toBe('rotate');
    expect(screen.getByRole('button', { name: /Rotate/i })).toHaveAttribute('aria-pressed', 'true');
  });

  it('sends the left mesh for the left viewer key', async () => {
    render(
      <AnatomyViewer
        selection={selection({
          viewerKey: 'skeleton.humerus.left',
          displayName: 'Left proximal humerus',
          side: 'LEFT',
        })}
      />
    );

    const scene = await screen.findByTestId('skeleton-scene');
    expect(scene.dataset.highlighted).toBe('Humerus_L');
    expect(scene.dataset.highlighted).not.toContain('Humerus_R');
    expect(scene.dataset.focusRegion).toBe('left_upper_arm');
  });

  it('sends every mesh of a multi-bone joint target', async () => {
    render(<AnatomyViewer selection={selection({ viewerKey: 'skeleton.knee.right', structure: 'KNEE' })} />);

    const scene = await screen.findByTestId('skeleton-scene');
    expect(scene.dataset.highlighted).toBe('Femur_R,Tibia_R,Patella_R');
    expect(screen.getByText(/Femur \(distal\), Tibia \(proximal\), Patella/)).toBeInTheDocument();
  });

  it('renders the brain target when the backend provides the nervous-system viewer key', async () => {
    render(
      <AnatomyViewer
        selection={selection({
          viewerKey: 'nervous.brain',
          structure: 'BRAIN',
          displayName: 'Brain',
          side: 'UNSPECIFIED',
          system: 'Nervous',
        })}
      />
    );

    const scene = await screen.findByTestId('skeleton-scene');
    expect(scene.dataset.highlighted).toContain('CerebralHemisphere_R');
    expect(scene.dataset.highlighted).toContain('CerebralHemisphere_L');
    expect(scene.dataset.highlighted).toContain('Cerebellum');
    expect(scene.dataset.focusRegion).toBe('head');
    expect(screen.getByText(/Highlighted structures: Brain, Brainstem, Cerebellum/)).toBeInTheDocument();
  });

  it('isolates and restores the skeleton, and resets the camera without losing the selection', async () => {
    const user = userEvent.setup();
    render(<AnatomyViewer selection={selection()} />);

    const scene = await screen.findByTestId('skeleton-scene');
    expect(scene.dataset.isolated).toBe('false');

    await user.click(screen.getByRole('button', { name: /Focus on Structure/i }));
    expect((await screen.findByTestId('skeleton-scene')).dataset.isolated).toBe('true');

    await user.click(screen.getByRole('button', { name: /Show Full Skeleton/i }));
    expect((await screen.findByTestId('skeleton-scene')).dataset.isolated).toBe('false');

    await user.click(screen.getByRole('button', { name: /Focus on Structure/i }));
    await user.click(screen.getByRole('button', { name: /Reset View/i }));

    const afterReset = await screen.findByTestId('skeleton-scene');
    expect(afterReset.dataset.isolated).toBe('false');
    expect(afterReset.dataset.resetNonce).toBe('1');
    // The clinical selection is untouched by a camera reset.
    expect(afterReset.dataset.highlighted).toBe('Humerus_R');
  });

  it('treats a click in the 3D view as exploration only, never as a finding', async () => {
    const user = userEvent.setup();
    render(<AnatomyViewer selection={selection()} />);

    await user.click(await screen.findByRole('button', { name: /simulate mesh click/i }));

    expect((await screen.findByTestId('skeleton-scene')).dataset.highlighted).toBe('Femur_L');
    expect((await screen.findByTestId('skeleton-scene')).dataset.selectionKey).toBe('skeleton.humerus.right');
    expect((await screen.findByTestId('skeleton-scene')).dataset.resetNonce).toBe('0');
    expect(screen.getByText(/not a clinical finding/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Reset View/i }));

    expect((await screen.findByTestId('skeleton-scene')).dataset.highlighted).toBe('Humerus_R');
    expect(screen.queryByText(/not a clinical finding/i)).not.toBeInTheDocument();
  });

  it('reports non-skeletal anatomy honestly and renders no scene', () => {
    render(
      <AnatomyViewer
        selection={selection({
          viewerKey: 'cardiac.heart',
          structure: 'HEART',
          system: 'Cardiovascular',
          displayName: 'Heart',
        })}
      />
    );

    expect(screen.getByText('3D model not available for this structure yet.')).toBeInTheDocument();
    expect(screen.queryByTestId('skeleton-scene')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Focus on Structure/i })).not.toBeInTheDocument();
  });

  it('reports a missing viewer key as having no single structure', () => {
    render(<AnatomyViewer selection={selection({ viewerKey: null, displayName: 'Bilateral pleura' })} />);

    expect(
      screen.getByText('No single 3D structure is available for this mapped finding.')
    ).toBeInTheDocument();
    expect(screen.queryByTestId('skeleton-scene')).not.toBeInTheDocument();
  });

  it('falls back safely when the 3D scene throws', async () => {
    sceneShouldThrow = true;
    render(<AnatomyViewer selection={selection()} />);

    expect(await screen.findByText('3D anatomy model could not be loaded')).toBeInTheDocument();
    expect(
      screen.getByText(/Review can continue from the mapped structure details below/i)
    ).toBeInTheDocument();
    expect(screen.queryByTestId('skeleton-scene')).not.toBeInTheDocument();
  });

  it('shows the BodyParts3D attribution and no placeholder disclosure for the bundled model', async () => {
    render(<AnatomyViewer selection={selection()} />);

    await screen.findByTestId('skeleton-scene');
    expect(
      screen.getByText(/BodyParts3D, © The Database Center for Life Science/)
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /CC BY 4\.0/ })).toHaveAttribute(
      'href',
      'https://creativecommons.org/licenses/by/4.0/'
    );
    expect(screen.queryByText(/Schematic placeholder skeleton/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/No licensed anatomy asset/i)).not.toBeInTheDocument();
  });

  it('keeps the mapped-structure metadata when the model fails to load', async () => {
    sceneShouldThrow = true;
    render(
      <MemoryRouter>
        <AnatomyPreview selection={selection()} linkedIssueType="LATERALITY_CONFLICT" />
      </MemoryRouter>
    );

    expect(await screen.findByText('3D anatomy model could not be loaded')).toBeInTheDocument();
    // Everything a reviewer needs is still on screen.
    expect(screen.getAllByText('Right proximal humerus').length).toBeGreaterThan(0);
    expect(screen.getByText('HUMERUS')).toBeInTheDocument();
    expect(screen.getByText('RIGHT')).toBeInTheDocument();
    expect(screen.getByText('PROXIMAL')).toBeInTheDocument();
    expect(screen.getByText('Source finding: Findings')).toBeInTheDocument();
    expect(
      screen.getByText(/BodyParts3D, © The Database Center for Life Science/)
    ).toBeInTheDocument();
  });
});
