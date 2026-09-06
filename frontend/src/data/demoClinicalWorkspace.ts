import type { ClinicalWorkspaceDemoCase } from '@/types/clinicalWorkspace';

export const demoClinicalWorkspace: ClinicalWorkspaceDemoCase = {
  study: {
    id: 'demo-study-shoulder-001',
    accessionNumber: 'ACC-20260902-1842',
    studyType: 'CT Shoulder',
    modality: 'CT',
    fileType: 'CT_SCAN',
    studyDate: '02 Sep 2026',
    reportStatus: 'REVIEW_REQUIRED',
    patient: {
      id: 'demo-patient-john-doe',
      fullName: 'John Doe',
      medicalRecordNumber: 'RAD-10291',
      dateOfBirth: '18 Apr 1968',
      ageLabel: '58y',
    },
  },
  report: {
    id: 'demo-report-shoulder-001',
    radiologist: 'Dr. Demo',
    createdAt: '14:36',
    sections: [
      {
        id: 'findings',
        title: 'Findings',
        body: [
          'There is a comminuted fracture involving the proximal right humerus.',
          'No acute dislocation.',
          'Mild surrounding soft tissue swelling is present.',
        ],
      },
      {
        id: 'comparison',
        title: 'Comparison',
        body: ['No prior shoulder examination is available.'],
      },
      {
        id: 'impression',
        title: 'Impression',
        body: ['Comminuted fracture of the proximal left humerus.'],
      },
    ],
    metadata: {
      reportStatus: 'REVIEW_REQUIRED',
      createdAt: '02 Sep 2026, 14:36',
      createdBy: 'Dr. Demo',
      lastUpdatedAt: null,
      lastUpdatedLabel: 'Not updated since creation',
    },
  },
  qaIssues: [
    {
      id: 'qa-laterality-humerus',
      severity: 'HIGH',
      type: 'LATERALITY_CONFLICT',
      message: 'Findings reference the RIGHT humerus while Impression references the LEFT humerus.',
      recommendation: 'Review laterality before final sign-off.',
      evidence: [
        {
          label: 'Findings',
          text: 'Comminuted fracture involving the proximal right humerus.',
        },
        {
          label: 'Impression',
          text: 'Comminuted fracture of the proximal left humerus.',
        },
      ],
      anatomySelection: {
        structure: 'HUMERUS',
        displayName: 'Right proximal humerus',
        side: 'RIGHT',
        region: 'PROXIMAL',
        system: 'Skeletal',
        viewerKey: 'skeleton.humerus.right',
        sourceLabel: 'Findings',
        sourceText: 'There is a comminuted fracture involving the proximal right humerus.',
      },
    },
    {
      id: 'qa-prior-comparison-gap',
      severity: 'MEDIUM',
      type: 'COMPARISON_GAP',
      message: 'No prior shoulder examination is available for interval comparison.',
      recommendation: 'Confirm whether outside prior imaging should be requested.',
      evidence: [
        {
          label: 'Comparison',
          text: 'No prior shoulder examination is available.',
        },
      ],
    },
  ],
  defaultAnatomySelection: {
    structure: 'SHOULDER',
    displayName: 'Right Shoulder',
    side: 'RIGHT',
    region: 'PROXIMAL',
    system: 'Musculoskeletal',
    viewerKey: 'skeleton.shoulder.right',
    sourceLabel: 'Findings',
    sourceText: 'There is a comminuted fracture involving the proximal right humerus.',
  },
  priorStudies: [
    {
      id: 'prior-none',
      date: 'No prior',
      studyType: 'Shoulder examination',
      modality: 'N/A',
      summary: 'No prior shoulder examination.',
    },
    {
      id: 'prior-chest',
      date: '12 Mar 2026',
      studyType: 'X-Ray Chest',
      modality: 'XRAY',
      summary: 'No acute osseous abnormality identified on included shoulder girdle views.',
    },
  ],
  structuredFindings: [
    {
      id: 'finding-fracture',
      label: 'Fracture',
      structure: 'Humerus',
      side: 'RIGHT',
      region: 'PROXIMAL',
      summary: 'Comminuted fracture involving the proximal right humerus.',
    },
    {
      id: 'finding-no-dislocation',
      label: 'No Dislocation',
      structure: 'Shoulder',
      side: 'RIGHT',
      summary: 'No acute glenohumeral dislocation.',
    },
  ],
  timeline: [
    {
      id: 'timeline-received',
      time: '14:31',
      label: 'Study received',
      detail: 'PACS/RIS intake placeholder',
    },
    {
      id: 'timeline-draft',
      time: '14:36',
      label: 'Draft report created',
      detail: 'Unsigned report text available in workspace',
    },
    {
      id: 'timeline-qa',
      time: '14:37',
      label: 'QA review pending',
      detail: 'Clinician review required',
    },
  ],
  audit: [
    {
      id: 'audit-created',
      label: 'Report created',
      actor: 'Dr. Demo',
      time: '14:36',
      detail: 'Demo draft created for workspace shell',
    },
    {
      id: 'audit-qa',
      label: 'QA review initiated',
      time: '14:37',
      detail: 'Demo QA issue set loaded locally',
    },
  ],
};
