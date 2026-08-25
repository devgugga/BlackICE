import { init as initCore } from '@cornerstonejs/core';
import {
  init as initTools,
  addTool,
  WindowLevelTool,
  ZoomTool,
  PanTool,
  StackScrollTool,
  LengthTool,
} from '@cornerstonejs/tools';
import dicomImageLoader from '@cornerstonejs/dicom-image-loader';
import { initMetadataProvider } from './viewer-metadata';

let initialization: Promise<void> | null = null;

async function initializeAll(): Promise<void> {
  await initCore();
  await initTools();

  dicomImageLoader.init({ maxWebWorkers: 1 });
  initMetadataProvider();

  addTool(WindowLevelTool);
  addTool(ZoomTool);
  addTool(PanTool);
  addTool(StackScrollTool);
  addTool(LengthTool);
}

export function initCornerstoneOnce(): Promise<void> {
  return (initialization ??= initializeAll());
}
