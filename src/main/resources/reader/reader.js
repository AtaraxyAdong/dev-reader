(() => {
  'use strict';
  const scroll = document.getElementById('reader-scroll');
  let settings = null;
  let anchor = { blockId: '', offsetRatio: 0 };
  let restoring = false;
  let scrollTimer;
  let resizeFrame;
  function send(kind, extra) {
    if (settings && window.readerBridge) window.readerBridge(JSON.stringify(Object.assign({ kind, token: settings.token }, extra || {})));
  }
  function blocks() { return Array.from(document.querySelectorAll('[data-reader-block]')).filter(el => el.getClientRects().length); }
  function capture() {
    const list = blocks();
    let block = list[0];
    for (const candidate of list) {
      if (candidate.offsetTop <= scroll.scrollTop + 3) block = candidate;
      else break;
    }
    if (!block) return { blockId: '', offsetRatio: 0 };
    return { blockId: block.id, offsetRatio: Math.max(0, Math.min(1, (scroll.scrollTop - block.offsetTop) / Math.max(1, block.offsetHeight))) };
  }
  function restore(position) {
    if (!position || !position.blockId) { scroll.scrollTop = 0; return; }
    const block = document.getElementById(position.blockId);
    if (block) scroll.scrollTop = block.offsetTop + (position.offsetRatio || 0) * block.offsetHeight;
  }
  function restoreAfterReflow() {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(() => {
      restoring = true;
      restore(anchor);
      requestAnimationFrame(() => { restoring = false; });
    });
  }
  window.readerApplyTheme = variables => {
    Object.keys(variables).forEach(name => document.documentElement.style.setProperty(name, variables[name]));
    restoreAfterReflow();
  };
  window.readerToggleAnswer = () => {
    const answer = document.getElementById('reference-answer');
    if (answer) answer.open = !answer.open;
  };
  window.readerStart = options => {
    if (options.token !== document.body.dataset.documentToken || (settings && settings.token === options.token)) return;
    settings = options;
    document.querySelectorAll('table').forEach(table => {
      const wrapper = document.createElement('div');
      wrapper.className = 'table-scroll';
      table.before(wrapper); wrapper.appendChild(table);
    });
    document.querySelectorAll('h1,h2,h3,h4,h5,h6,p,pre,table,ul,ol,blockquote,summary').forEach((block, i) => {
      const section = block.closest('[data-section]');
      block.id = (section ? section.dataset.section : 'document') + '-' + (block.id || 'block-' + i);
      block.setAttribute('data-reader-block', '');
    });
    const answer = document.getElementById('reference-answer');
    if (answer) {
      answer.open = options.answerExpanded !== false;
      answer.addEventListener('toggle', () => send('answer', { expanded: answer.open }));
    }
    document.querySelectorAll('img').forEach(image => {
      image.addEventListener('load', restoreAfterReflow);
      const showFailure = () => {
        if (!image.isConnected) return;
        const link = document.createElement('a');
        link.href = image.src; link.className = 'image-failure'; link.textContent = options.imageFailure;
        image.replaceWith(link); restoreAfterReflow();
      };
      image.addEventListener('error', showFailure);
      if (image.complete && image.naturalWidth === 0) showFailure();
    });
    window.readerApplyTheme(options.theme);
    anchor = options.position || anchor;
    restoreAfterReflow();
    new ResizeObserver(restoreAfterReflow).observe(scroll);
    send('ready');
  };
  scroll.addEventListener('scroll', () => {
    if (restoring) return;
    anchor = capture();
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(() => send('position', anchor), 70);
  }, { passive: true });
  document.addEventListener('click', event => {
    const link = event.target.closest('a[href]');
    if (!link) return;
    event.preventDefault();
    const target = new URL(link.href, location.href);
    const source = settings && settings.sourceUri ? new URL(settings.sourceUri) : null;
    const sameDocument = target.origin === location.origin && target.pathname === location.pathname;
    const sameSource = source && target.origin === source.origin && target.pathname === source.pathname;
    if ((sameDocument || sameSource) && target.hash) {
      const key = decodeURIComponent(target.hash.substring(1));
      const slug = text => text.trim().toLowerCase().replace(/[^\p{L}\p{N}\s_-]/gu, '').replace(/\s+/g, '-');
      const node = document.getElementById(key) || Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,h6')).find(heading => slug(heading.textContent) === slug(key));
      if (node) node.scrollIntoView();
    } else send('link', { url: target.href });
  });
})();
