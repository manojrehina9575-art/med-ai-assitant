import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AnatomyViewer } from './AnatomyViewer';
import type { AnatomySelection } from '@/types/clinicalWorkspace';

vi.mock('./utils/webgl', () => ({ supportsWebGl: () => true }));
vi.mock('./SkeletonScene', () => ({ default: () => <div data-testid="skeleton-scene" /> }));

// Simulates the development fallback: no licensed model configured.
vi.mock('./model/anatomyViewerManifest', async () => {
  const actual = await vi.importActual<typeof import('./model/anatomyViewerManifest')>(
    './model/anatomyViewerManifest'
  );
  return {
    ...actual,
    skeletonModelUrl: null,
    usingBundledSkeletonModel: false,
    skeletonModelAttribution: null,
    skeletonModelLicense: null,
  };
});

const selection: AnatomySelection = {
  structure: 'HUMERUS',
  displayName: 'Right proximal humerus',
  side: 'RIGHT',
  region: 'PROXIMAL',
  system: 'Skeletal',
  viewerKey: 'skeleton.humerus.right',
};

describe('AnatomyViewer without a licensed model', () => {
  afterEach(() => cleanup());

  it('discloses the schematic placeholder and never presents it as BodyParts3D', async () => {
    render(<AnatomyViewer selection={selection} />);

    await screen.findByTestId('skeleton-scene');
    expect(screen.getByText(/Schematic placeholder skeleton/i)).toBeInTheDocument();
    expect(screen.getByText(/No licensed anatomy asset is loaded/i)).toBeInTheDocument();
    expect(screen.queryByText(/BodyParts3D/)).not.toBeInTheDocument();
  });
});
