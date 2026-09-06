import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ImageOff, Images, Plus } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/Card';
import { fileService } from '@/services/fileService';
import { cn } from '@/utils/cn';
import type { MedicalFile } from '@/types';

interface StudyImagesPanelProps {
  /** Real patient id to fetch files for. Null in demo mode, where there is no backend patient record. */
  patientId: string | null;
}

type ImagesTab = 'study' | 'key';

export function StudyImagesPanel({ patientId }: StudyImagesPanelProps) {
  const [files, setFiles] = useState<MedicalFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<ImagesTab>('study');

  useEffect(() => {
    setTab('study');
    if (!patientId) {
      setFiles([]);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    fileService
      .list(patientId, 0, 20)
      .then((page) => {
        if (cancelled) return;
        setFiles(page.content);
      })
      .catch(() => {
        if (cancelled) return;
        setError('Could not load study images for this patient.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [patientId]);

  const imageFiles = files.filter((file) => IMAGE_FILE_TYPES.has(file.fileType));

  return (
    <Card className="min-w-0">
      <div className="flex items-center gap-1 border-b p-3" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
        <TabButton active={tab === 'study'} onClick={() => setTab('study')}>
          Study Images ({imageFiles.length})
        </TabButton>
        <TabButton active={tab === 'key'} onClick={() => setTab('key')}>
          Key Images (0)
        </TabButton>
      </div>

      <CardContent className="p-3">
        {!patientId ? (
          <EmptyState icon={ImageOff} message="Demo case — no linked files to display." />
        ) : loading ? (
          <div className="flex min-h-24 items-center justify-center text-xs text-slate-500">Loading study images...</div>
        ) : error ? (
          <div className="flex min-h-24 items-center justify-center text-xs text-red-300">{error}</div>
        ) : tab === 'key' ? (
          <EmptyState icon={Images} message="No key images marked yet." />
        ) : (
          <div className="flex flex-wrap gap-3">
            {imageFiles.map((file, index) => (
              <a
                key={file.id}
                href={fileService.getViewUrl(patientId, file.id)}
                target="_blank"
                rel="noreferrer"
                className="group block w-28 shrink-0"
              >
                <div
                  className="flex h-20 w-28 items-center justify-center overflow-hidden rounded-lg border bg-slate-950/40 transition-colors group-hover:border-blue-500/50"
                  style={{ borderColor: 'var(--clr-border, #1e2d45)' }}
                >
                  <img
                    src={fileService.getViewUrl(patientId, file.id)}
                    alt={file.originalFileName ?? `Study image ${index + 1}`}
                    className="h-full w-full object-cover"
                    loading="lazy"
                  />
                </div>
                <p className="mt-1 truncate text-[10px] text-slate-500">{index + 1}. {file.originalFileName ?? file.fileType}</p>
              </a>
            ))}

            <Link
              to="/upload"
              className="flex h-20 w-28 shrink-0 flex-col items-center justify-center gap-1 rounded-lg border border-dashed text-slate-500 transition-colors hover:border-blue-500/50 hover:text-blue-300"
              style={{ borderColor: 'var(--clr-border-2, #243250)' }}
            >
              <Plus className="h-4 w-4" />
              <span className="text-[10px] font-semibold">Add Image</span>
            </Link>

            {imageFiles.length === 0 && (
              <p className="self-center text-xs text-slate-500">No study images uploaded for this patient yet.</p>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

const IMAGE_FILE_TYPES = new Set(['XRAY', 'CT_SCAN', 'ULTRASOUND', 'MRI']);

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
        active ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
      )}
    >
      {children}
    </button>
  );
}

function EmptyState({ icon: Icon, message }: { icon: typeof ImageOff; message: string }) {
  return (
    <div className="flex min-h-24 flex-col items-center justify-center gap-2 text-center">
      <Icon className="h-5 w-5 text-slate-600" />
      <p className="text-xs text-slate-500">{message}</p>
    </div>
  );
}
