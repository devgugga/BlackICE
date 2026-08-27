# Cornerstone3D Integration within Vue 3

How to integrate **Cornerstone3D** (`@cornerstonejs/core`, `@cornerstonejs/tools`, `@cornerstonejs/dicom-image-loader`) into Vue 3 components while avoiding memory leaks and performance bottlenecks.

## Cornerstone3D Core Concepts

- **`RenderingEngine`**: Orchestrates WebGL rendering. Typically one instance per application lifecycle.
- **Viewport**: Display canvas attached to a DOM element (`<div>`).
  - `Stack` Viewport: Navigates a 2D sequence of `imageId`s (standard MVP use case).
  - `Volume` Viewport: 3D / Multi-Planar Reconstruction (MPR).
- **Image Loaders**: Resolve image schemes. For DICOMweb, use the `wadors:` loader (e.g. `wadors:https://host/api/dicomweb/studies/{u}/series/{u}/instances/{u}/frames/1`).
- **Tools + ToolGroup**: WindowLevel, Zoom, Pan, StackScroll, Length measurements, etc. Tools are registered globally and assigned to a `ToolGroup` attached to the viewport.

## Component Mounting Lifecycle

1. Invoke `init()` for core and tools **once** during application startup.
2. Configure `dicom-image-loader` to point to the secure same-origin WADO frame proxy endpoint exposed by Quarkus. The BFF session remains in the `HttpOnly` cookie; no access tokens are read or injected in client JavaScript.
3. Build `imageId`s using **WADO-RS** frame URLs (never QIDO, as QIDO delivers only JSON metadata). The list of instance/frame UIDs comes from QIDO; actual pixel payloads come from WADO.
4. Inside the component: `onMounted` -> capture the `<div>` element via template `ref`, instantiate/retrieve the `RenderingEngine`, enable the viewport on the element, set the stack, attach the `ToolGroup`, and call `render()`.
5. `onUnmounted` / Series switching -> **Destroy and clean up**: disable prefetching (`stackPrefetch.disable`), purge pool queues (`imageRetrievalPoolManager.clearRequestStack`, `imageLoadPoolManager.clearRequestStack`), remove annotations (`annotation.state.removeAllAnnotations`), destroy the `ToolGroup` (`destroyToolGroup`), disable the `RenderingEngine`, and clear metadata providers. Failure to do so leaks WebGL contexts and triggers browser crashes.
6. **Capability Gate:** Initialize the WebGL/Cornerstone runtime only on supported viewport dimensions (desktop and landscape tablets >= 768px). On narrow mobile screens, display a clinical capability notice without instantiating Cornerstone or allocating WebGL memory.

## Critical Gotcha: Vue Reactivity vs. Cornerstone Objects

Vue 3's reactivity system wraps objects in JavaScript `Proxy` instances. **Never** place `RenderingEngine`, viewports, volumes, or `ToolGroup` objects inside `ref()`, `reactive()`, or reactive Pinia stores: the Proxy breaks internal WebGL instance identity and degrades rendering performance.

- Store these instances using **`shallowRef`** or mark them with **`markRaw`**.
- What should be reactive: string IDs, active frame indices, Window/Level presets, active UI tool names. The Cornerstone internal engine objects must remain non-reactive.

## Standard MVP Tools

WindowLevel (contrast/brightness), Zoom, Pan, StackScroll (series/frame navigation), and Length (measurement). One `ToolGroup` per viewport; change active tools by updating mouse bindings rather than recreating the viewport.

## Review Checklist

- [ ] `imageId` constructed from WADO-RS frame URLs (not QIDO)?
- [ ] Image loader uses the same-origin WADO proxy without client-side token injection?
- [ ] Cornerstone objects isolated from Vue reactivity (`shallowRef` or `markRaw`)?
- [ ] `onUnmounted` destroys rendering engine/viewport, clears prefetch/load pools, annotations, and metadata?
- [ ] Viewer runtime protected by resolution Capability Gate (no WebGL instantiation on mobile)?
- [ ] `init()` for core/tools executed exactly once on app boot?
