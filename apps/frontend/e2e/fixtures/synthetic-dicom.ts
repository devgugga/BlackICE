const SECONDARY_CAPTURE = '1.2.840.10008.5.1.4.1.1.7';
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
    text(0x0020, 0x0011, 'IS', '1'),
    text(0x0020, 0x0013, 'IS', '1'),
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
