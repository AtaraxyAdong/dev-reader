(() => {
  'use strict';
  const preview = document.getElementById('image-preview');
  const previewImage = document.getElementById('image-preview-image');
  const previewViewport = document.getElementById('image-preview-viewport');
  const previewCanvas = document.getElementById('image-preview-canvas');
  const previewStatus = document.getElementById('image-preview-status');
  const previewScale = document.getElementById('image-preview-scale');
  const previewClose = document.getElementById('image-preview-close');
  const zoomControls = ['out', 'in', 'fit', 'actual'].map(name => document.getElementById('image-preview-' + name));
  let previewGeneration = 0;
  let previewLoaded = false;
  let zoom = 1;
  let fitted = true;
  function fitScale() {
    return Math.min(1, Math.max(1, previewViewport.clientWidth - 24) / previewImage.naturalWidth,
      Math.max(1, previewViewport.clientHeight - 24) / previewImage.naturalHeight);
  }
  function setZoom(value, center = true) {
    if (!previewLoaded || !preview.open) return;
    const vw = previewViewport.clientWidth, vh = previewViewport.clientHeight;
    const oldWidth = previewImage.naturalWidth * zoom, oldHeight = previewImage.naturalHeight * zoom;
    const x = center ? (previewViewport.scrollLeft + vw / 2 - (previewCanvas.clientWidth - oldWidth) / 2) / oldWidth : .5;
    const y = center ? (previewViewport.scrollTop + vh / 2 - (previewCanvas.clientHeight - oldHeight) / 2) / oldHeight : .5;
    zoom = Math.max(Math.min(.1, fitScale()), Math.min(8, value));
    const width = previewImage.naturalWidth * zoom, height = previewImage.naturalHeight * zoom;
    const cw = Math.max(vw, width + 24), ch = Math.max(vh, height + 24);
    previewCanvas.style.width = cw + 'px'; previewCanvas.style.height = ch + 'px';
    previewImage.style.width = width + 'px'; previewImage.style.height = height + 'px';
    previewImage.style.left = (cw - width) / 2 + 'px'; previewImage.style.top = (ch - height) / 2 + 'px';
    previewViewport.scrollLeft = (cw - width) / 2 + x * width - vw / 2;
    previewViewport.scrollTop = (ch - height) / 2 + y * height - vh / 2;
    previewScale.textContent = Math.round(zoom * 100) + '%';
  }
  function closeImagePreview(notify = true) {
    const wasOpen = preview.open;
    ++previewGeneration;
    if (preview.open) preview.close();
    previewImage.onload = null; previewImage.onerror = null;
    previewImage.removeAttribute('src'); previewImage.hidden = true;
    previewImage.alt = ''; previewLoaded = false; previewStatus.textContent = '';
    previewCanvas.removeAttribute('style'); previewImage.removeAttribute('style');
    if (notify && wasOpen && window.readerPreviewClosed) window.readerPreviewClosed();
  }
  window.readerShowImagePreview = (value, alt) => {
    let url;
    try { url = new URL(value, location.href); } catch (_) { return; }
    // Keep exactly the existing HTTP(S) image permission boundary; never navigate or execute a URL.
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return;
    closeImagePreview(false);
    fitted = true; zoom = 1;
    previewStatus.textContent = '正在加载图片…'; previewScale.textContent = '—';
    zoomControls.forEach(button => { button.disabled = true; });
    previewImage.alt = alt || '图片预览';
    const generation = previewGeneration;
    previewImage.onload = () => {
      if (generation !== previewGeneration || !preview.open) return;
      previewLoaded = true; previewImage.hidden = false; previewStatus.textContent = '';
      zoomControls.forEach(button => { button.disabled = false; });
      setZoom(fitScale(), false);
    };
    previewImage.onerror = () => {
      if (generation !== previewGeneration || !preview.open) return;
      previewImage.hidden = true; previewStatus.textContent = '图片加载失败，请关闭后重试。';
    };
    preview.showModal(); previewClose.focus({ preventScroll: true });
    previewImage.src = url.href;
  };
  previewClose.addEventListener('click', () => closeImagePreview());
  preview.addEventListener('cancel', event => { event.preventDefault(); closeImagePreview(); });
  preview.addEventListener('click', event => { if (event.target === preview) closeImagePreview(); });
  document.getElementById('image-preview-fit').addEventListener('click', () => { fitted = true; setZoom(fitScale(), false); });
  document.getElementById('image-preview-actual').addEventListener('click', () => { fitted = false; setZoom(1); });
  document.getElementById('image-preview-in').addEventListener('click', () => { fitted = false; setZoom(zoom * 1.25); });
  document.getElementById('image-preview-out').addEventListener('click', () => { fitted = false; setZoom(zoom / 1.25); });
  new ResizeObserver(() => { if (preview.open && previewLoaded) setZoom(fitted ? fitScale() : zoom); }).observe(previewViewport);
  window.addEventListener('pagehide', () => closeImagePreview(false));
  document.addEventListener('keydown', event => {
    if (preview.open) {
      if (event.key === 'Tab') {
        const controls = Array.from(preview.querySelectorAll('button:not(:disabled), [tabindex="0"]'));
        const first = controls[0], last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
      return;
    }
  });
})();
