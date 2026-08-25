const SECONDARY_CAPTURE = '1.2.840.10008.5.1.4.1.1.7';
export const CT_IMAGE_STORAGE = '1.2.840.10008.5.1.4.1.1.2';
const EXPLICIT_VR_LE = '1.2.840.10008.1.2.1';
const IMPLEMENTATION_UID = '2.25.999999999999999999999999999999999999';
const LONG_VR = new Set(['OB', 'OD', 'OF', 'OL', 'OV', 'OW', 'SQ', 'SV', 'UC', 'UR', 'UT', 'UV', 'UN']);

function paddedText(value: string, vr: string): Buffer {
  const raw = Buffer.from(value, 'ascii');
  if (raw.length % 2 === 0) return raw;
  return Buffer.concat([raw, Buffer.from([vr === 'UI' ? 0 : 0x20])]);
}

function element(group: number, tag: number, vr: string, value: Buffer): Buffer {
  const long = LONG_VR.has(vr);
  const header = Buffer.alloc(long ? 12 : 8);
  header.writeUInt16LE(group, 0);
  header.writeUInt16LE(tag, 2);
  header.write(vr, 4, 2, 'ascii');
  if (long) header.writeUInt32LE(value.length, 8);
  else header.writeUInt16LE(value.length, 6);
  return Buffer.concat([header, value]);
}

function text(group: number, tag: number, vr: string, value: string): Buffer {
  return element(group, tag, vr, paddedText(value, vr));
}

function us(group: number, tag: number, value: number): Buffer {
  const data = Buffer.alloc(2);
  data.writeUInt16LE(value);
  return element(group, tag, 'US', data);
}

export interface SyntheticDicomMetadata {
  patientName?: string;
  patientId?: string;
  patientIdIssuer?: string;
  studyDate?: string;
  studyTime?: string;
  modality?: string;
  studyDescription?: string;
  seriesDescription?: string;
  seriesNumber?: number;
  instanceNumber?: number;
}

export function createSyntheticDicom(
  studyUid: string,
  seriesUid: string,
  sopUid: string,
  metadata: SyntheticDicomMetadata = {},
): Buffer {
  const metaBody = Buffer.concat([
    element(0x0002, 0x0001, 'OB', Buffer.from([0, 1])),
    text(0x0002, 0x0002, 'UI', SECONDARY_CAPTURE),
    text(0x0002, 0x0003, 'UI', sopUid),
    text(0x0002, 0x0010, 'UI', EXPLICIT_VR_LE),
    text(0x0002, 0x0012, 'UI', IMPLEMENTATION_UID),
  ]);
  const metaLength = Buffer.alloc(4);
  metaLength.writeUInt32LE(metaBody.length);

  const datasetElements: Buffer[] = [
    text(0x0008, 0x0016, 'UI', SECONDARY_CAPTURE),
    text(0x0008, 0x0018, 'UI', sopUid),
    text(0x0008, 0x0020, 'DA', (metadata.studyDate ?? '20260809').replace(/-/g, '')),
    text(0x0008, 0x0030, 'TM', (metadata.studyTime ?? '120000').replace(/:/g, '')),
    text(0x0008, 0x0050, 'SH', 'SYNTHETIC'),
    text(0x0008, 0x0060, 'CS', metadata.modality ?? 'OT'),
    text(0x0008, 0x0064, 'CS', 'WSD'),
  ];

  if (metadata.studyDescription) {
    datasetElements.push(text(0x0008, 0x1030, 'LO', metadata.studyDescription));
  }

  if (metadata.seriesDescription) {
    datasetElements.push(text(0x0008, 0x103e, 'LO', metadata.seriesDescription));
  }

  datasetElements.push(
    text(0x0010, 0x0010, 'PN', metadata.patientName ?? 'SYNTHETIC^BLACKICE'),
    text(0x0010, 0x0020, 'LO', metadata.patientId ?? 'SYNTHETIC'),
  );

  if (metadata.patientIdIssuer) {
    datasetElements.push(text(0x0010, 0x0021, 'LO', metadata.patientIdIssuer));
  }

  datasetElements.push(
    text(0x0020, 0x000d, 'UI', studyUid),
    text(0x0020, 0x000e, 'UI', seriesUid),
    text(0x0020, 0x0010, 'SH', 'TEST'),
    text(0x0020, 0x0011, 'IS', String(metadata.seriesNumber ?? 1)),
    text(0x0020, 0x0013, 'IS', String(metadata.instanceNumber ?? 1)),
    us(0x0028, 0x0002, 1),
    text(0x0028, 0x0004, 'CS', 'MONOCHROME2'),
    us(0x0028, 0x0010, 1),
    us(0x0028, 0x0011, 1),
    us(0x0028, 0x0100, 8),
    us(0x0028, 0x0101, 8),
    us(0x0028, 0x0102, 7),
    us(0x0028, 0x0103, 0),
    element(0x7fe0, 0x0010, 'OB', Buffer.from([0, 0])),
  );

  const dataset = Buffer.concat(datasetElements);

  return Buffer.concat([
    Buffer.alloc(128),
    Buffer.from('DICM', 'ascii'),
    element(0x0002, 0x0000, 'UL', metaLength),
    metaBody,
    dataset,
  ]);
}

export interface SyntheticCtSliceMetadata extends SyntheticDicomMetadata {
  seriesNumber?: number;
  seriesDescription?: string;
  instanceNumber?: number;
  sliceLocation?: number;
  imagePositionPatient?: [number, number, number];
  imageOrientationPatient?: [number, number, number, number, number, number];
  pixelSpacing?: [number, number];
  frameOfReferenceUid?: string;
  rescaleIntercept?: number;
  rescaleSlope?: number;
  windowCenter?: number | number[];
  windowWidth?: number | number[];
}

export function createSyntheticCtSlice(
  studyUid: string,
  seriesUid: string,
  sopUid: string,
  metadata: SyntheticCtSliceMetadata = {},
): Buffer {
  const metaBody = Buffer.concat([
    element(0x0002, 0x0001, 'OB', Buffer.from([0, 1])),
    text(0x0002, 0x0002, 'UI', CT_IMAGE_STORAGE),
    text(0x0002, 0x0003, 'UI', sopUid),
    text(0x0002, 0x0010, 'UI', EXPLICIT_VR_LE),
    text(0x0002, 0x0012, 'UI', IMPLEMENTATION_UID),
  ]);
  const metaLength = Buffer.alloc(4);
  metaLength.writeUInt32LE(metaBody.length);

  const instanceNumber = metadata.instanceNumber ?? 1;
  const seriesNumber = metadata.seriesNumber ?? 1;
  const sliceLocation = metadata.sliceLocation ?? (instanceNumber - 1) * 5.0;
  const ipp = metadata.imagePositionPatient ?? [0, 0, sliceLocation];
  const iop = metadata.imageOrientationPatient ?? [1, 0, 0, 0, 1, 0];
  const spacing = metadata.pixelSpacing ?? [0.7, 0.7];
  const frameOfRef = metadata.frameOfReferenceUid ?? `${studyUid}.9999`;

  const wc = metadata.windowCenter !== undefined
    ? (Array.isArray(metadata.windowCenter) ? metadata.windowCenter.join('\\') : String(metadata.windowCenter))
    : '40';
  const ww = metadata.windowWidth !== undefined
    ? (Array.isArray(metadata.windowWidth) ? metadata.windowWidth.join('\\') : String(metadata.windowWidth))
    : '400';

  const datasetElements: Buffer[] = [
    text(0x0008, 0x0016, 'UI', CT_IMAGE_STORAGE),
    text(0x0008, 0x0018, 'UI', sopUid),
    text(0x0008, 0x0020, 'DA', (metadata.studyDate ?? '20260822').replace(/-/g, '')),
    text(0x0008, 0x0030, 'TM', (metadata.studyTime ?? '120000').replace(/:/g, '')),
    text(0x0008, 0x0050, 'SH', 'SYNTHETIC'),
    text(0x0008, 0x0060, 'CS', metadata.modality ?? 'CT'),
  ];

  if (metadata.studyDescription) {
    datasetElements.push(text(0x0008, 0x1030, 'LO', metadata.studyDescription));
  }

  if (metadata.seriesDescription) {
    datasetElements.push(text(0x0008, 0x103e, 'LO', metadata.seriesDescription));
  }

  datasetElements.push(
    text(0x0010, 0x0010, 'PN', metadata.patientName ?? 'SYNTHETIC^PATIENT'),
    text(0x0010, 0x0020, 'LO', metadata.patientId ?? 'SYNTHETIC'),
  );

  if (metadata.patientIdIssuer) {
    datasetElements.push(text(0x0010, 0x0021, 'LO', metadata.patientIdIssuer));
  }

  const numPixels = 512 * 512;
  const pixelData = Buffer.alloc(numPixels * 2);
  for (let r = 0; r < 512; r++) {
    for (let c = 0; c < 512; c++) {
      const idx = (r * 512 + c) * 2;
      const dx = r - 256;
      const dy = c - 256;
      const dist = Math.sqrt(dx * dx + dy * dy);
      let val = 0;
      if (dist < 180) {
        val = 1024 + Math.floor((r / 512) * 200) + instanceNumber * 10;
      }
      if (dist < 185 && dist >= 175) {
        val = 2000;
      }
      pixelData.writeInt16LE(val, idx);
    }
  }

  datasetElements.push(
    text(0x0020, 0x000d, 'UI', studyUid),
    text(0x0020, 0x000e, 'UI', seriesUid),
    text(0x0020, 0x0010, 'SH', 'CT'),
    text(0x0020, 0x0011, 'IS', String(seriesNumber)),
    text(0x0020, 0x0013, 'IS', String(instanceNumber)),
    text(0x0020, 0x0032, 'DS', `${ipp[0]}\\${ipp[1]}\\${ipp[2]}`),
    text(0x0020, 0x0037, 'DS', `${iop[0]}\\${iop[1]}\\${iop[2]}\\${iop[3]}\\${iop[4]}\\${iop[5]}`),
    text(0x0020, 0x0052, 'UI', frameOfRef),
    us(0x0028, 0x0002, 1),
    text(0x0028, 0x0004, 'CS', 'MONOCHROME2'),
    us(0x0028, 0x0010, 512),
    us(0x0028, 0x0011, 512),
    text(0x0028, 0x0030, 'DS', `${spacing[0]}\\${spacing[1]}`),
    us(0x0028, 0x0100, 16),
    us(0x0028, 0x0101, 12),
    us(0x0028, 0x0102, 11),
    us(0x0028, 0x0103, 1),
    text(0x0028, 0x1050, 'DS', wc),
    text(0x0028, 0x1051, 'DS', ww),
    text(0x0028, 0x1052, 'DS', String(metadata.rescaleIntercept ?? -1024)),
    text(0x0028, 0x1053, 'DS', String(metadata.rescaleSlope ?? 1)),
    element(0x7fe0, 0x0010, 'OW', pixelData),
  );

  const dataset = Buffer.concat(datasetElements);

  return Buffer.concat([
    Buffer.alloc(128),
    Buffer.from('DICM', 'ascii'),
    element(0x0002, 0x0000, 'UL', metaLength),
    metaBody,
    dataset,
  ]);
}
