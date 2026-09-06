/**
 * Suni TV — Web App Core Engine
 * Zero-dependency, native modern JS with Hls.js & DecompressionStream
 */

// Application State
const state = {
  tab: 'tv',               // 'tv' | 'radio' | 'webcams' | 'favs'
  country: 'ir',           // 'ir' by default, 'all' for worldwide, or 2-letter code
  category: 'all',         // category filter
  search: '',              // search query
  proxyMode: 'direct',     // 'direct' | 'cf' | 'allorigins'
  quality: 'auto',         // 'auto' | '720' | '480' | '360' | '240'
  playing: null,           // current channel object
  favs: JSON.parse(localStorage.getItem('suni_favs') || '[]'),
  
  // Datasets
  countries: {},           // code -> country metadata
  iran: { tv: [], radio: [] },
  data: {
    tv: null,
    radio: null,
    webcams: null
  },
  
  // UI Pagination
  visibleChannels: [],
  batchSize: 60,
  currentRenderIndex: 0,
  
  // HLS Instance
  hls: null
};

// DOM Elements
const el = {
  navTabs: document.querySelectorAll('.nav-tab'),
  tvCount: document.getElementById('tvCount'),
  radioCount: document.getElementById('radioCount'),
  webcamCount: document.getElementById('webcamCount'),
  favsCount: document.getElementById('favsCount'),
  
  // Player
  playerStage: document.getElementById('playerStage'),
  videoContainer: document.getElementById('videoContainer'),
  youtubeContainer: document.getElementById('youtubeContainer'),
  radioDeck: document.getElementById('radioDeck'),
  videoPlayer: document.getElementById('videoPlayer'),
  youtubeIframe: document.getElementById('youtubeIframe'),
  audioPlayer: document.getElementById('audioPlayer'),
  playerLoader: document.getElementById('playerLoader'),
  radioTitle: document.getElementById('radioTitle'),
  radioSub: document.getElementById('radioSub'),
  
  // Meta Bar
  playingFlag: document.getElementById('playingFlag'),
  playingTitle: document.getElementById('playingTitle'),
  playingCountry: document.getElementById('playingCountry'),
  playingCategory: document.getElementById('playingCategory'),
  playingQuality: document.getElementById('playingQuality'),
  btnFav: document.getElementById('btnFav'),
  btnPip: document.getElementById('btnPip'),
  btnVlc: document.getElementById('btnVlc'),
  btnCopyUrl: document.getElementById('btnCopyUrl'),
  btnFullscreen: document.getElementById('btnFullscreen'),
  
  // Settings
  proxyButtons: document.querySelectorAll('#proxyToggle .toggle-btn'),
  qualitySelect: document.getElementById('qualitySelect'),
  playerError: document.getElementById('playerError'),
  errorMessage: document.getElementById('errorMessage'),
  btnRetryProxy: document.getElementById('btnRetryProxy'),
  
  // Filters
  searchInput: document.getElementById('searchInput'),
  searchClear: document.getElementById('searchClear'),
  btnCountryModal: document.getElementById('btnCountryModal'),
  selectedCountryLabel: document.getElementById('selectedCountryLabel'),
  categorySelect: document.getElementById('categorySelect'),
  quickPills: document.querySelectorAll('.quick-pill'),
  iranChannelsCount: document.getElementById('iranChannelsCount'),
  
  // Catalog
  catalogTitle: document.getElementById('catalogTitle'),
  catalogCount: document.getElementById('catalogCount'),
  channelsGrid: document.getElementById('channelsGrid'),
  emptyState: document.getElementById('emptyState'),
  btnResetFilters: document.getElementById('btnResetFilters'),
  btnRandom: document.getElementById('btnRandom'),
  scrollSentinel: document.getElementById('scrollSentinel'),
  
  // Mini Player
  miniPlayer: document.getElementById('miniPlayer'),
  miniTitle: document.getElementById('miniTitle'),
  miniPlayPause: document.getElementById('miniPlayPause'),
  miniExpand: document.getElementById('miniExpand'),
  miniClose: document.getElementById('miniClose'),
  
  // Modal
  countryModal: document.getElementById('countryModal'),
  modalClose: document.getElementById('modalClose'),
  countrySearchInput: document.getElementById('countrySearchInput'),
  countryList: document.getElementById('countryList')
};

/* ==========================================================================
   Flag & Country Helpers
   ========================================================================== */
function codeToFlag(code) {
  if (!code || code.length !== 2) return '🌐';
  const c = code.toUpperCase();
  const first = 0x1F1E6 + (c.charCodeAt(0) - 65);
  const second = 0x1F1E6 + (c.charCodeAt(1) - 65);
  return String.fromCodePoint(first, second);
}

function getChannelId(ch) {
  return ch.nanoid || ch.id || `${ch.country || ''}_${ch.name}`;
}

function getStreamUrl(ch) {
  if (!ch) return '';
  if (ch.sources?.streams && ch.sources.streams.length > 0) return ch.sources.streams[0];
  if (ch.sources?.youtube && ch.sources.youtube.length > 0) return ch.sources.youtube[0];
  if (ch.url) return ch.url;
  return '';
}

function isYouTubeStream(url) {
  if (!url) return false;
  return url.includes('youtube.com') || url.includes('youtu.be');
}

function extractYouTubeId(url) {
  const m = url.match(/(?:youtu\.be\/|youtube\.com\/(?:embed\/|v\/|watch\?v=|live\/))([\w-]{11})/);
  return m ? m[1] : null;
}

/* ==========================================================================
   Proxy URL Rewriting
   ========================================================================== */
function applyProxy(url) {
  if (!url || state.proxyMode === 'direct') return url;
  
  if (state.proxyMode === 'cf') {
    // Standard robust CORS proxy
    return `https://corsproxy.io/?url=${encodeURIComponent(url)}`;
  } else if (state.proxyMode === 'allorigins') {
    return `https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`;
  }
  return url;
}

/* ==========================================================================
   DecompressionStream / Gzip Fetch
   ========================================================================== */
async function fetchGzipJson(url) {
  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`Fetch failed: HTTP ${resp.status}`);
  
  // Native DecompressionStream in modern browsers
  if (typeof DecompressionStream !== 'undefined') {
    const ds = new DecompressionStream('gzip');
    const decompressedStream = resp.body.pipeThrough(ds);
    const text = await new Response(decompressedStream).text();
    return JSON.parse(text);
  } else {
    // Fallback: direct JSON in case served uncompressed
    return await resp.json();
  }
}

/* ==========================================================================
   Data Initialization
   ========================================================================== */
async function initData() {
  try {
    updateFavsCount();

    // 1. Fetch Countries metadata & Persian bundle concurrently
    const [countriesResp, iranResp] = await Promise.all([
      fetch('data/countries.json').then(r => r.json()),
      fetch('data/iran.json').then(r => r.json())
    ]);
    
    state.countries = countriesResp;
    state.iran = iranResp;
    
    // Set counts in UI
    el.iranChannelsCount.textContent = (state.iran.tv?.length || 91).toLocaleString('fa-IR');
    
    // 2. Render initial channel list immediately (Iran TV channels in < 50ms!)
    renderCatalog();
    
    // Auto-play or stage the first popular channel (e.g. 4 Music or IRIB)
    if (state.iran.tv?.length > 0) {
      stageChannel(state.iran.tv[0], false);
    }
    
    // 3. Background stream full TV dataset
    loadFullDataset('tv');
    
  } catch (err) {
    console.error('Error loading initial data:', err);
    el.channelsGrid.innerHTML = `
      <div class="empty-state">
        <span class="empty-icon">⚠️</span>
        <h4 class="empty-title">خطا در دریافت اطلاعات</h4>
        <p class="empty-desc">لطفاً اتصال اینترنت خود را بررسی کنید و صفحه را بازنشانی نمایید.</p>
      </div>`;
  }
}

async function loadFullDataset(kind) {
  if (state.data[kind]) return state.data[kind];
  
  try {
    const d = await fetchGzipJson(`data/${kind}.json.gz`);
    state.data[kind] = d;
    
    // Update badge counts
    if (kind === 'tv') {
      const count = d.by_category?.all?.length || 6612;
      el.tvCount.textContent = count.toLocaleString('fa-IR');
    } else if (kind === 'radio') {
      const count = d.by_category?.all?.length || 26932;
      el.radioCount.textContent = count.toLocaleString('fa-IR');
    } else if (kind === 'webcams') {
      const count = d.by_category?.all?.length || 4143;
      el.webcamCount.textContent = count.toLocaleString('fa-IR');
    }
    
    // Re-render if current tab matches
    if (state.tab === kind) {
      renderCatalog();
    }
    return d;
  } catch (e) {
    console.warn(`Could not load full dataset for ${kind}:`, e);
    return null;
  }
}

/* ==========================================================================
   Catalog Query & Filter Engine
   ========================================================================== */
function getActiveChannelsPool() {
  if (state.tab === 'favs') {
    return state.favs;
  }
  
  // If dataset not yet loaded, return fast iran bundle
  const fullData = state.data[state.tab];
  if (!fullData) {
    if (state.country === 'ir' && (state.tab === 'tv' || state.tab === 'radio')) {
      return state.iran[state.tab] || [];
    }
    return [];
  }
  
  let list = [];
  if (state.country === 'all') {
    list = fullData.by_category?.all || [];
  } else {
    list = fullData.by_country?.[state.country] || [];
  }
  return list;
}

function filterChannels() {
  let pool = getActiveChannelsPool();
  
  // Category Filter
  if (state.category !== 'all') {
    pool = pool.filter(c => {
      const cats = (c.categories || []).map(x => x.toLowerCase());
      return cats.some(cat => cat.includes(state.category.toLowerCase()));
    });
  }
  
  // Search Query Filter
  if (state.search.trim()) {
    const q = state.search.trim().toLowerCase();
    pool = pool.filter(c => {
      const name = (c.name || '').toLowerCase();
      const country = (c.country || '').toLowerCase();
      const cats = (c.categories || []).join(' ').toLowerCase();
      return name.includes(q) || country.includes(q) || cats.includes(q);
    });
  }
  
  return pool;
}

function renderCatalog(resetScroll = true) {
  state.visibleChannels = filterChannels();
  state.currentRenderIndex = 0;
  el.channelsGrid.innerHTML = '';
  
  // Update header summary
  let countryName = 'همه جهان';
  if (state.country === 'ir') countryName = 'ایران و کانال‌های فارسی';
  else if (state.country !== 'all') {
    countryName = state.countries[state.country]?.name || state.country.toUpperCase();
  }
  
  let tabName = 'تلویزیون';
  if (state.tab === 'radio') tabName = 'ایستگاه‌های رادیویی';
  else if (state.tab === 'webcams') tabName = 'وبکم‌های زنده';
  else if (state.tab === 'favs') tabName = 'علاقه‌مندی‌های شما';
  
  el.catalogTitle.textContent = `${tabName} · ${countryName}`;
  el.catalogCount.textContent = `${state.visibleChannels.length.toLocaleString('fa-IR')} مورد`;
  
  if (state.visibleChannels.length === 0) {
    el.emptyState.style.display = 'flex';
  } else {
    el.emptyState.style.display = 'none';
    renderMoreChannels();
  }
  
  if (resetScroll) {
    // smoothly scroll to catalog if needed
  }
}

function renderMoreChannels() {
  const nextSlice = state.visibleChannels.slice(
    state.currentRenderIndex,
    state.currentRenderIndex + state.batchSize
  );
  
  if (nextSlice.length === 0) return;
  
  const frag = document.createDocumentFragment();
  nextSlice.forEach(ch => {
    const card = createChannelCard(ch);
    frag.appendChild(card);
  });
  
  el.channelsGrid.appendChild(frag);
  state.currentRenderIndex += nextSlice.length;
}

function createChannelCard(ch) {
  const card = document.createElement('div');
  const chId = getChannelId(ch);
  const isPlaying = state.playing && getChannelId(state.playing) === chId;
  const isFav = state.favs.some(f => getChannelId(f) === chId);
  
  card.className = `channel-card ${isPlaying ? 'active-playing' : ''}`;
  card.dataset.id = chId;
  
  const flag = codeToFlag(ch.country);
  const cat = (ch.categories && ch.categories.length > 0) ? ch.categories[0] : (state.tab === 'radio' ? 'رادیو' : 'عمومی');
  
  card.innerHTML = `
    <div class="channel-card-top">
      <div class="card-avatar">${flag}</div>
      <button class="card-fav-btn ${isFav ? 'is-fav' : ''}" title="علاقه‌مندی" data-fav-btn>
        ${isFav ? '❤️' : '🤍'}
      </button>
    </div>
    <div class="card-name" title="${ch.name || ''}">${ch.name || 'شبکه بدون نام'}</div>
    <div class="card-meta">
      <span class="card-badge">${cat}</span>
      <span>${(ch.country || '').toUpperCase()}</span>
    </div>
  `;
  
  // Click on card plays channel
  card.addEventListener('click', (e) => {
    if (e.target.closest('[data-fav-btn]')) return;
    playChannel(ch);
  });
  
  // Favorite click
  const favBtn = card.querySelector('[data-fav-btn]');
  favBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleFavorite(ch);
    const nowFav = state.favs.some(f => getChannelId(f) === chId);
    favBtn.textContent = nowFav ? '❤️' : '🤍';
    favBtn.classList.toggle('is-fav', nowFav);
  });
  
  return card;
}

/* ==========================================================================
   Player Engine (HLS.js, YouTube, Audio)
   ========================================================================== */
function stageChannel(ch, autoPlay = true) {
  state.playing = ch;
  
  // Update Meta Bar
  el.playingFlag.textContent = codeToFlag(ch.country);
  el.playingTitle.textContent = ch.name || 'شبکه انتخابی';
  el.playingCountry.textContent = (ch.country || '').toUpperCase();
  el.playingCategory.textContent = (ch.categories && ch.categories[0]) || (state.tab === 'radio' ? 'رادیو' : 'عمومی');
  
  // Favorite state
  const isFav = state.favs.some(f => getChannelId(f) === getChannelId(ch));
  el.btnFav.textContent = isFav ? '❤️' : '🤍';
  
  // Highlight card in grid
  document.querySelectorAll('.channel-card.active-playing').forEach(c => c.classList.remove('active-playing'));
  const activeCard = document.querySelector(`.channel-card[data-id="${getChannelId(ch)}"]`);
  if (activeCard) activeCard.classList.add('active-playing');
  
  // Update Mini player
  el.miniTitle.textContent = `${codeToFlag(ch.country)} ${ch.name}`;
  
  if (autoPlay) {
    playCurrentStream();
  }
}

function playChannel(ch) {
  stageChannel(ch, true);
  
  // Scroll smoothly to player if not in view
  const playerRect = el.playerStage.getBoundingClientRect();
  if (playerRect.top < -100) {
    el.playerStage.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}

function playCurrentStream() {
  const ch = state.playing;
  if (!ch) return;
  
  hideError();
  const rawUrl = getStreamUrl(ch);
  if (!rawUrl) {
    showError('آدرس جریان پخش برای این شبکه یافت نشد.');
    return;
  }
  
  // Check YouTube
  if (isYouTubeStream(rawUrl)) {
    playYouTube(rawUrl);
    return;
  }
  
  // Check Radio vs Video
  const isRadio = state.tab === 'radio' || (!rawUrl.includes('.m3u8') && (rawUrl.endsWith('.mp3') || rawUrl.endsWith('.aac')));
  if (isRadio) {
    playAudio(rawUrl);
  } else {
    playVideo(rawUrl);
  }
}

function playYouTube(url) {
  stopAllMedia();
  const videoId = extractYouTubeId(url);
  if (!videoId) {
    showError('لینک یوتوب معتبر نیست.');
    return;
  }
  
  el.videoContainer.style.display = 'none';
  el.radioDeck.style.display = 'none';
  el.youtubeContainer.style.display = 'block';
  el.youtubeIframe.src = `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&rel=0&modestbranding=1`;
}

function playAudio(url) {
  stopAllMedia();
  el.videoContainer.style.display = 'none';
  el.youtubeContainer.style.display = 'none';
  el.radioDeck.style.display = 'flex';
  
  el.radioTitle.textContent = state.playing?.name || 'ایستگاه رادیویی';
  el.radioSub.textContent = `${codeToFlag(state.playing?.country)} پخش زنده استریم صدا`;
  
  const finalUrl = applyProxy(url);
  el.audioPlayer.src = finalUrl;
  el.audioPlayer.play().catch(e => {
    console.warn('Audio auto-play blocked or error:', e);
    showError('خطا در پخش رادیو — دکمه پخش را لمس کنید یا از پروکسی ضد فیلتر استفاده کنید.');
  });
  
  setupMediaSession(state.playing);
}

function playVideo(url) {
  stopAllMedia();
  el.radioDeck.style.display = 'none';
  el.youtubeContainer.style.display = 'none';
  el.videoContainer.style.display = 'block';
  el.playerLoader.classList.add('active');
  
  const finalUrl = applyProxy(url);
  const video = el.videoPlayer;
  
  if (Hls.isSupported() && finalUrl.includes('.m3u8')) {
    if (state.hls) {
      state.hls.destroy();
    }
    
    const hls = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      backBufferLength: 30,
      manifestLoadingTimeOut: 12000,
      manifestLoadingMaxRetry: 2
    });
    
    state.hls = hls;
    hls.loadSource(finalUrl);
    hls.attachMedia(video);
    
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      el.playerLoader.classList.remove('active');
      applyQualityLevel();
      video.play().catch(() => {});
    });
    
    hls.on(Hls.Events.ERROR, (event, data) => {
      if (data.fatal) {
        el.playerLoader.classList.remove('active');
        switch (data.type) {
          case Hls.ErrorTypes.NETWORK_ERROR:
            console.error('Fatal network error:', data);
            // If on direct mode, prompt or auto-retry with proxy
            if (state.proxyMode === 'direct') {
              showError('خطای شبکه / دسترسی به جریان مسدود است. برای رفع مشکل کلیک کنید:');
            } else {
              showError('جریان در حال حاضر پاسخ نمی‌دهد (آفلاین یا تحریم).');
            }
            hls.destroy();
            break;
          case Hls.ErrorTypes.MEDIA_ERROR:
            console.warn('Media error, trying recovery...');
            hls.recoverMediaError();
            break;
          default:
            hls.destroy();
            showError('امکان بارگذاری این جریان وجود ندارد.');
            break;
        }
      }
    });
    
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari Native HLS
    video.src = finalUrl;
    video.addEventListener('loadedmetadata', () => {
      el.playerLoader.classList.remove('active');
      video.play().catch(() => {});
    }, { once: true });
    
    video.addEventListener('error', () => {
      el.playerLoader.classList.remove('active');
      showError('خطا در پخش جریان — لطفاً پروکسی ضد فیلتر را امتحان کنید.');
    }, { once: true });
  } else {
    // Direct Progressive Stream (MP4, etc.)
    video.src = finalUrl;
    video.play().catch(() => {});
    el.playerLoader.classList.remove('active');
  }
  
  setupMediaSession(state.playing);
}

function stopAllMedia() {
  if (state.hls) {
    state.hls.destroy();
    state.hls = null;
  }
  el.videoPlayer.pause();
  el.videoPlayer.removeAttribute('src');
  el.videoPlayer.load();
  
  el.audioPlayer.pause();
  el.audioPlayer.removeAttribute('src');
  el.audioPlayer.load();
  
  el.youtubeIframe.src = '';
  el.playerLoader.classList.remove('active');
}

function applyQualityLevel() {
  if (!state.hls) return;
  const levels = state.hls.levels;
  if (!levels || levels.length === 0) return;
  
  if (state.quality === 'auto') {
    state.hls.currentLevel = -1; // Auto ABR
  } else {
    const targetHeight = parseInt(state.quality, 10);
    // Find closest height
    let bestIdx = -1;
    let minDiff = Infinity;
    levels.forEach((l, idx) => {
      const diff = Math.abs((l.height || 0) - targetHeight);
      if (diff < minDiff) {
        minDiff = diff;
        bestIdx = idx;
      }
    });
    if (bestIdx !== -1) {
      state.hls.currentLevel = bestIdx;
    }
  }
}

function setupMediaSession(ch) {
  if (!('mediaSession' in navigator) || !ch) return;
  
  navigator.mediaSession.metadata = new MediaMetadata({
    title: ch.name || 'Suni TV',
    artist: `${codeToFlag(ch.country)} ${(ch.categories && ch.categories[0]) || 'Live Stream'}`,
    album: 'Suni TV World Online',
    artwork: [
      { src: 'icon.png', sizes: '512x512', type: 'image/png' }
    ]
  });
  
  navigator.mediaSession.setActionHandler('play', () => {
    if (el.videoPlayer.src) el.videoPlayer.play();
    if (el.audioPlayer.src) el.audioPlayer.play();
  });
  navigator.mediaSession.setActionHandler('pause', () => {
    if (el.videoPlayer.src) el.videoPlayer.pause();
    if (el.audioPlayer.src) el.audioPlayer.pause();
  });
}

function showError(msg) {
  el.errorMessage.textContent = msg;
  el.playerError.style.display = 'flex';
}

function hideError() {
  el.playerError.style.display = 'none';
}

/* ==========================================================================
   Favorites Storage
   ========================================================================== */
function toggleFavorite(ch) {
  const chId = getChannelId(ch);
  const idx = state.favs.findIndex(f => getChannelId(f) === chId);
  if (idx !== -1) {
    state.favs.splice(idx, 1);
  } else {
    state.favs.unshift(ch);
  }
  localStorage.setItem('suni_favs', JSON.stringify(state.favs));
  updateFavsCount();
  
  if (state.tab === 'favs') {
    renderCatalog();
  }
}

function updateFavsCount() {
  el.favsCount.textContent = state.favs.length.toLocaleString('fa-IR');
}

/* ==========================================================================
   Event Listeners & UI Handlers
   ========================================================================== */
function setupEvents() {

  // 1. Navigation Tabs
  el.navTabs.forEach(tabBtn => {
    tabBtn.addEventListener('click', async () => {
      el.navTabs.forEach(b => b.classList.remove('active'));
      tabBtn.classList.add('active');
      state.tab = tabBtn.dataset.tab;
      
      // Lazy load full dataset if switching to radio or webcams
      if (state.tab === 'radio' || state.tab === 'webcams') {
        if (!state.data[state.tab]) {
          el.channelsGrid.innerHTML = `
            <div class="loading-card">
              <div class="spinner"></div>
              <p>در حال دانلود و پردازش فهرست ${state.tab === 'radio' ? 'رادیو' : 'وبکم‌ها'}...</p>
            </div>`;
          await loadFullDataset(state.tab);
        }
      }
      
      renderCatalog(true);
    });
  });

  // 2. Search Bar
  let searchTimer = null;
  el.searchInput.addEventListener('input', (e) => {
    const val = e.target.value;
    el.searchClear.style.display = val ? 'block' : 'none';
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.search = val;
      renderCatalog(false);
    }, 120);
  });

  el.searchClear.addEventListener('click', () => {
    el.searchInput.value = '';
    el.searchClear.style.display = 'none';
    state.search = '';
    renderCatalog(false);
  });

  // 3. Category Filter
  el.categorySelect.addEventListener('change', (e) => {
    state.category = e.target.value;
    renderCatalog(true);
  });

  // 4. Quick Filter Pills
  el.quickPills.forEach(pill => {
    pill.addEventListener('click', () => {
      el.quickPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      const filter = pill.dataset.filter;
      state.country = filter;
      
      // Update country dropdown label
      if (filter === 'all') el.selectedCountryLabel.textContent = 'همه کشورها (۲۴۶)';
      else if (filter === 'ir') el.selectedCountryLabel.textContent = '🇮🇷 ایران';
      else {
        el.selectedCountryLabel.textContent = `${codeToFlag(filter)} ${filter.toUpperCase()}`;
      }
      
      renderCatalog(true);
    });
  });

  // 5. Proxy Selector
  el.proxyButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      el.proxyButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.proxyMode = btn.dataset.proxy;
      if (state.playing) {
        playCurrentStream();
      }
    });
  });

  el.btnRetryProxy.addEventListener('click', () => {
    // Switch to CF proxy and retry
    el.proxyButtons.forEach(b => b.classList.toggle('active', b.dataset.proxy === 'cf'));
    state.proxyMode = 'cf';
    playCurrentStream();
  });

  // 6. Quality Selector
  el.qualitySelect.addEventListener('change', (e) => {
    state.quality = e.target.value;
    applyQualityLevel();
  });

  // 7. Player Toolbar Actions
  el.btnFav.addEventListener('click', () => {
    if (state.playing) {
      toggleFavorite(state.playing);
      const isFav = state.favs.some(f => getChannelId(f) === getChannelId(state.playing));
      el.btnFav.textContent = isFav ? '❤️' : '🤍';
    }
  });

  el.btnPip.addEventListener('click', async () => {
    if (document.pictureInPictureElement) {
      await document.exitPictureInPicture();
    } else if (el.videoPlayer && el.videoPlayer.readyState >= 1) {
      await el.videoPlayer.requestPictureInPicture();
    }
  });

  el.btnFullscreen.addEventListener('click', () => {
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      el.playerStage.requestFullscreen().catch(() => {});
    }
  });

  el.btnCopyUrl.addEventListener('click', () => {
    const streamUrl = getStreamUrl(state.playing);
    if (!streamUrl) return;
    navigator.clipboard.writeText(streamUrl).then(() => {
      const originalText = el.btnCopyUrl.innerHTML;
      el.btnCopyUrl.innerHTML = '<span class="tool-icon">✅</span> کپی شد';
      setTimeout(() => el.btnCopyUrl.innerHTML = originalText, 1800);
    });
  });

  el.btnVlc.addEventListener('click', () => {
    const streamUrl = getStreamUrl(state.playing);
    if (!streamUrl) return;
    // Potplayer / VLC direct intent or m3u8 download
    window.location.href = `vlc://${streamUrl}`;
  });

  // 8. Random Channel Button
  el.btnRandom.addEventListener('click', () => {
    const pool = state.visibleChannels;
    if (pool.length === 0) return;
    const randomCh = pool[Math.floor(Math.random() * pool.length)];
    playChannel(randomCh);
  });

  // 9. Reset Filters
  el.btnResetFilters.addEventListener('click', () => {
    state.country = 'ir';
    state.category = 'all';
    state.search = '';
    el.searchInput.value = '';
    el.categorySelect.value = 'all';
    el.quickPills.forEach(p => p.classList.toggle('active', p.dataset.filter === 'iran'));
    renderCatalog(true);
  });

  // 10. Country Modal
  el.btnCountryModal.addEventListener('click', openCountryModal);
  el.modalClose.addEventListener('click', closeCountryModal);
  el.countryModal.addEventListener('click', (e) => {
    if (e.target === el.countryModal) closeCountryModal();
  });

  el.countrySearchInput.addEventListener('input', (e) => {
    renderCountryList(e.target.value);
  });

  // 11. Infinite Scroll Sentinel
  const scrollObserver = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting) {
      renderMoreChannels();
    }
  }, { rootMargin: '400px' });
  scrollObserver.observe(el.scrollSentinel);

  // 12. Floating Mini Player Trigger on Scroll
  const miniPlayerObserver = new IntersectionObserver((entries) => {
    const isPlayerInView = entries[0].isIntersecting;
    const isMediaActive = (!el.videoPlayer.paused && el.videoPlayer.src) || (!el.audioPlayer.paused && el.audioPlayer.src);
    
    if (!isPlayerInView && isMediaActive) {
      el.miniPlayer.style.display = 'block';
    } else {
      el.miniPlayer.style.display = 'none';
    }
  }, { threshold: 0.1 });
  miniPlayerObserver.observe(el.playerStage);

  el.miniExpand.addEventListener('click', () => {
    el.playerStage.scrollIntoView({ behavior: 'smooth', block: 'start' });
    el.miniPlayer.style.display = 'none';
  });

  el.miniClose.addEventListener('click', () => {
    el.miniPlayer.style.display = 'none';
  });

  el.miniPlayPause.addEventListener('click', () => {
    if (el.videoPlayer.src) {
      if (el.videoPlayer.paused) {
        el.videoPlayer.play();
        el.miniPlayPause.textContent = '⏸️';
      } else {
        el.videoPlayer.pause();
        el.miniPlayPause.textContent = '▶️';
      }
    } else if (el.audioPlayer.src) {
      if (el.audioPlayer.paused) {
        el.audioPlayer.play();
        el.miniPlayPause.textContent = '⏸️';
      } else {
        el.audioPlayer.pause();
        el.miniPlayPause.textContent = '▶️';
      }
    }
  });

  // PWA Service Worker Registration
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
}

/* ==========================================================================
   Country Modal Logic
   ========================================================================== */
function openCountryModal() {
  el.countryModal.style.display = 'flex';
  el.countrySearchInput.value = '';
  renderCountryList('');
  setTimeout(() => el.countrySearchInput.focus(), 100);
}

function closeCountryModal() {
  el.countryModal.style.display = 'none';
}

function renderCountryList(query) {
  const q = query.trim().toLowerCase();
  const list = Object.values(state.countries).sort((a, b) => {
    // Iran always top
    if (a.code === 'ir') return -1;
    if (b.code === 'ir') return 1;
    return (b[state.tab] || 0) - (a[state.tab] || 0);
  });
  
  el.countryList.innerHTML = '';
  
  // Worldwide Option
  const allItem = document.createElement('div');
  allItem.className = `country-item ${state.country === 'all' ? 'active' : ''}`;
  allItem.innerHTML = `
    <div class="c-left">
      <span class="c-flag">🌐</span>
      <span>همه کشورها (سراسر جهان)</span>
    </div>
  `;
  allItem.addEventListener('click', () => selectCountry('all', 'همه کشورها (۲۴۶)'));
  el.countryList.appendChild(allItem);

  list.forEach(c => {
    const name = c.name || '';
    const native = c.native || '';
    if (q && !name.toLowerCase().includes(q) && !native.toLowerCase().includes(q) && !c.code.includes(q)) {
      return;
    }
    
    const count = c[state.tab] || 0;
    if (count === 0 && !q) return; // skip 0 channels unless searching
    
    const item = document.createElement('div');
    item.className = `country-item ${state.country === c.code ? 'active' : ''}`;
    item.innerHTML = `
      <div class="c-left">
        <span class="c-flag">${codeToFlag(c.code)}</span>
        <span>${name} ${native && native !== name ? `(${native})` : ''}</span>
      </div>
      <span class="c-count">${count.toLocaleString('fa-IR')} شبکه</span>
    `;
    item.addEventListener('click', () => selectCountry(c.code, `${codeToFlag(c.code)} ${name}`));
    el.countryList.appendChild(item);
  });
}

function selectCountry(code, label) {
  state.country = code;
  el.selectedCountryLabel.textContent = label;
  
  // Update quick pills
  el.quickPills.forEach(p => {
    p.classList.toggle('active', p.dataset.filter === (code === 'ir' ? 'iran' : code));
  });
  
  closeCountryModal();
  renderCatalog(true);
}

// Kickoff
document.addEventListener('DOMContentLoaded', () => {
  setupEvents();
  initData();
});
