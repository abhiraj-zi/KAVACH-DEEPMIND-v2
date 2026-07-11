document.addEventListener('DOMContentLoaded', () => {
    // UI Elements
    const appBody = document.getElementById('appBody');
    const hubView = document.getElementById('hubView');
    const activeView = document.getElementById('activeView');
    const triggerBtn = document.getElementById('triggerBtn');
    const resolveBtn = document.getElementById('resolveBtn');
    const modeToggle = document.getElementById('modeToggle');
    const modeLabel = document.getElementById('modeLabel');
    const eventLog = document.getElementById('eventLog');
    const activeTitle = document.getElementById('activeTitle');
    const activeSubtitle = document.getElementById('activeSubtitle');
    const deadScreenHint = document.getElementById('deadScreenHint');
    const dsPill = document.getElementById('dsPill');
    const dsModel = document.getElementById('dsModel');
    const dsCount = document.getElementById('dsCount');
    const dsActivity = document.getElementById('dsActivity');
    const netDot = document.getElementById('netDot');
    const netStatus = document.getElementById('netStatus');
    const clockEl = document.getElementById('clock');
    const incidentTimer = document.getElementById('incidentTimer');
    const threatMeter = document.getElementById('threatMeter');
    const threatLabel = document.getElementById('threatLabel');
    const agentRoster = document.getElementById('agentRoster');
    const mapZoneName = document.getElementById('mapZoneName');
    const mapEta = document.getElementById('mapEta');
    const mapEtaWrap = document.getElementById('mapEtaWrap');
    const mapHudLabel = document.getElementById('mapHudLabel');
    const zoneList = document.getElementById('zoneList');
    const zoneListMsg = document.getElementById('zoneListMsg');
    const zoneSource = document.getElementById('zoneSource');

    // State
    let isOffline = false;
    let isDefenseActive = false;
    let sseSource = null;
    let audioContext = null;
    let oscillator = null;
    let timerHandle = null;
    let incidentStart = 0;
    let gemmaInferences = 0;   // on-device inference counter for the dark-screen pill

    // Agent metadata: display name -> icon + accent
    const AGENTS = {
        'Antigravity':  { icon: 'hub',              color: 'iceWhite'    },
        'Computer Use': { icon: 'explore',          color: 'iceWhite'    },
        'Live Voice':   { icon: 'phone_in_talk',    color: 'safetyGreen' },
        'Omni':         { icon: 'info',             color: 'cyberOrange' },
        'Gemma':        { icon: 'memory',           color: 'cyberOrange' },
        'System':       { icon: 'bolt',             color: 'crimsonRed'  },
    };

    // Live wall clock
    setInterval(() => {
        clockEl.textContent = new Date().toLocaleTimeString('en-GB');
    }, 1000);
    clockEl.textContent = new Date().toLocaleTimeString('en-GB');

    // Initialization
    modeToggle.addEventListener('change', (e) => setOfflineMode(e.target.checked));
    triggerBtn.addEventListener('click', triggerCodeRed);
    resolveBtn.addEventListener('click', resolveIncident);

    // Double tap dead screen to restore
    let lastTap = 0;
    appBody.addEventListener('touchend', (e) => {
        const currentTime = new Date().getTime();
        const tapLength = currentTime - lastTap;
        if (isOffline && isDefenseActive && tapLength < 500 && tapLength > 0) {
            modeToggle.checked = false;
            setOfflineMode(false);
            e.preventDefault();
        }
        lastTap = currentTime;
    });

    appBody.addEventListener('dblclick', () => {
        if (isOffline && isDefenseActive) {
            modeToggle.checked = false;
            setOfflineMode(false);
        }
    });

    // Triple tap body to trigger Code Red
    let tapCount = 0;
    let tapTimer = null;
    appBody.addEventListener('click', (e) => {
        if (e.target.closest('button') || e.target.closest('input') || e.target.closest('label')) return;
        if (!isDefenseActive) {
            tapCount++;
            if (tapCount >= 3) {
                tapCount = 0;
                clearTimeout(tapTimer);
                triggerCodeRed();
            } else {
                clearTimeout(tapTimer);
                tapTimer = setTimeout(() => { tapCount = 0; }, 1000);
            }
        }
    });

    // Physical side buttons (Volume Keys) to trigger Code Red
    let volumeTapCount = 0;
    let volumeTapTimer = null;
    document.addEventListener('keydown', (e) => {
        if (e.key === 'VolumeUp' || e.key === 'VolumeDown' ||
            e.key === 'AudioVolumeUp' || e.key === 'AudioVolumeDown' ||
            e.keyCode === 227 || e.keyCode === 228 || e.keyCode === 175 || e.keyCode === 174) {
            if (!isDefenseActive) {
                volumeTapCount++;
                if (volumeTapCount >= 3) {
                    volumeTapCount = 0;
                    clearTimeout(volumeTapTimer);
                    triggerCodeRed();
                } else {
                    clearTimeout(volumeTapTimer);
                    volumeTapTimer = setTimeout(() => { volumeTapCount = 0; }, 1500);
                }
            }
        }
    });

    // --- Fluid view swap: exit-animate old, enter-animate new ---
    function swapViews(fromEl, toEl) {
        fromEl.classList.add('view-exit');
        setTimeout(() => {
            fromEl.classList.add('hidden');
            fromEl.classList.remove('flex', 'view-exit');
            toEl.classList.remove('hidden');
            toEl.classList.add('flex', 'view-enter');
            setTimeout(() => toEl.classList.remove('view-enter'), 550);
        }, 300);
    }

    function setOfflineMode(offline) {
        isOffline = offline;
        modeLabel.textContent = isOffline ? "DARK SURVIVAL (OFF)" : "GHOST OPERATOR (ON)";
        modeLabel.className = `text-xs font-bold tracking-wider ${isOffline ? 'text-cyberOrange' : 'text-safetyGreen'}`;
        netStatus.textContent = isOffline ? "NO SIGNAL · ON-DEVICE" : "SECURE LINK";
        netDot.style.background = isOffline ? 'var(--orange)' : 'var(--green)';

        if (isDefenseActive) {
            if (isOffline) activateDarkSurvival();
            else activateGhostOperator();
        }

        fetch('/trigger', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: "mode_switch", mode: isOffline ? "offline" : "online" })
        }).catch(() => console.log("Backend offline, mode switched locally."));
    }

    function buildRoster() {
        const order = ['Antigravity', 'Computer Use', 'Live Voice', 'Omni'];
        agentRoster.innerHTML = order.map(name => {
            const a = AGENTS[name];
            return `<div class="flex items-center gap-1.5 bg-surfaceGray/60 border border-borderGray rounded-full pl-1 pr-2.5 py-1"
                         data-agent="${name}" title="${name}">
                        <span class="material-icons text-[13px] text-mutedSlate agent-ico">${a.icon}</span>
                        <span class="w-1.5 h-1.5 rounded-full bg-mutedSlate agent-led"></span>
                    </div>`;
        }).join('');
    }

    function pingRoster(name) {
        const chip = agentRoster.querySelector(`[data-agent="${name}"]`);
        if (!chip) return;
        const led = chip.querySelector('.agent-led');
        const ico = chip.querySelector('.agent-ico');
        led.style.background = 'var(--green)';
        led.style.boxShadow = '0 0 6px var(--green)';
        ico.classList.remove('text-mutedSlate');
        ico.classList.add('text-iceWhite');
    }

    function startTimer() {
        incidentStart = Date.now();
        clearInterval(timerHandle);
        timerHandle = setInterval(() => {
            const s = Math.floor((Date.now() - incidentStart) / 1000);
            const mm = String(Math.floor(s / 60)).padStart(2, '0');
            const ss = String(s % 60).padStart(2, '0');
            incidentTimer.textContent = `${mm}:${ss}`;
        }, 500);
    }

    function setThreat(pct, label) {
        threatMeter.style.width = pct + '%';
        threatLabel.textContent = label;
    }

    // Threat level driven live by Omni events
    const THREAT_STYLE = {
        HIGH:   { pct: 82, grad: 'from-cyberOrange to-crimsonRed',  color: 'crimsonRed'  },
        MEDIUM: { pct: 52, grad: 'from-safetyGreen to-cyberOrange', color: 'cyberOrange' },
        LOW:    { pct: 26, grad: 'from-safetyGreen to-safetyGreen', color: 'safetyGreen' },
    };
    function applyThreat(level, conf) {
        const s = THREAT_STYLE[level];
        if (!s) return;
        threatMeter.style.width = s.pct + '%';
        threatMeter.className = 'meter-fill h-full rounded-full bg-gradient-to-r ' + s.grad;
        threatLabel.textContent = conf ? `${level} · ${conf}%` : level;
        threatLabel.className = 'text-[10px] font-mono font-bold text-' + s.color;
    }

    // --- Live map: REAL safe zones from OpenStreetMap (Overpass) ---
    // No fabricated places — every zone below is a live OSM feature near the
    // user's actual coordinates. Routing uses the public OSRM demo server;
    // if any external service is unreachable we say so rather than bluff.

    // Fallback coords are the general Marathahalli / Rohini Tech Park area and
    // are LABELLED approximate. Live browser geolocation takes precedence.
    const FALLBACK_LOC = { lat: 12.9557, lng: 77.7008, approx: true };

    let mapInstance = null;
    let userMarker = null;
    let zoneLayer = null;
    let routeLayer = null;
    let currentUserLoc = null;
    let currentZones = [];

    function haversine(a, b) {
        const R = 6371000, toRad = d => d * Math.PI / 180;
        const dLat = toRad(b.lat - a.lat), dLng = toRad(b.lng - a.lng);
        const s = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
        return 2 * R * Math.asin(Math.sqrt(s));
    }
    const fmtDist = m => m < 1000 ? Math.round(m) + ' m'
        : (m / 1000).toFixed(m < 10000 ? 1 : 0) + ' km';
    const fmtEtaDrive = sec => '~' + Math.max(1, Math.round(sec / 60)) + ' min';
    const fmtEtaWalk = m => '~' + Math.max(1, Math.round(m / 1000 / 4.5 * 60)) + ' min walk';
    const escapeHtml = s => { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; };

    function getUserLocation() {
        return new Promise(resolve => {
            if (!navigator.geolocation) return resolve({ ...FALLBACK_LOC });
            navigator.geolocation.getCurrentPosition(
                pos => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude, approx: false }),
                () => resolve({ ...FALLBACK_LOC }),
                { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 }
            );
        });
    }

    function categorize(tags) {
        const a = tags.amenity, s = tags.shop, r = tags.railway;
        if (a === 'police')       return { cat: 'Police',      icon: 'local_police',          cls: 'z-police',   color: '#FF3366' };
        if (a === 'hospital')     return { cat: 'Hospital',    icon: 'local_hospital',        cls: 'z-hospital', color: '#00E676' };
        if (a === 'clinic')       return { cat: 'Clinic',      icon: 'medical_services',      cls: 'z-hospital', color: '#00E676' };
        if (a === 'fire_station') return { cat: 'Fire Station',icon: 'local_fire_department', cls: 'z-fire',     color: '#FFA000' };
        if (a === 'fuel')         return { cat: 'Fuel (24/7)', icon: 'local_gas_station',     cls: 'z-public',   color: '#78909C' };
        if (s === 'mall')         return { cat: 'Mall',        icon: 'storefront',            cls: 'z-public',   color: '#78909C' };
        if (a === 'bus_station')  return { cat: 'Transit Hub', icon: 'directions_bus',        cls: 'z-public',   color: '#78909C' };
        if (r === 'station')      return { cat: 'Transit Hub', icon: 'directions_transit',    cls: 'z-public',   color: '#78909C' };
        return null;
    }

    function overpassQL(lat, lng, radius) {
        const q = f => `nwr[${f}](around:${radius},${lat},${lng});`;
        return '[out:json][timeout:25];(' + [
            '"amenity"="police"', '"amenity"="hospital"', '"amenity"="clinic"',
            '"amenity"="fire_station"', '"amenity"="fuel"', '"shop"="mall"',
            '"amenity"="bus_station"', '"railway"="station"',
        ].map(q).join('') + ');out center tags;';
    }

    async function fetchSafeZones(lat, lng, radius = 3000) {
        const body = 'data=' + encodeURIComponent(overpassQL(lat, lng, radius));
        const endpoints = [
            'https://overpass-api.de/api/interpreter',
            'https://overpass.kumi.systems/api/interpreter',
        ];
        let data = null, lastErr = null;
        for (const url of endpoints) {
            try {
                const res = await fetch(url, { method: 'POST', body });
                if (!res.ok) throw new Error('HTTP ' + res.status);
                data = await res.json();
                break;
            } catch (e) { lastErr = e; }
        }
        if (!data) throw lastErr || new Error('Overpass unreachable');

        const seen = new Set(), zones = [];
        for (const el of (data.elements || [])) {
            const la = el.lat != null ? el.lat : (el.center && el.center.lat);
            const ln = el.lon != null ? el.lon : (el.center && el.center.lon);
            if (la == null || ln == null) continue;
            const tags = el.tags || {};
            const meta = categorize(tags);
            if (!meta) continue;
            const name = tags.name || tags['name:en'] || meta.cat;
            const key = name + '@' + la.toFixed(4) + ',' + ln.toFixed(4);
            if (seen.has(key)) continue;
            seen.add(key);
            zones.push({
                id: (el.type || 'n') + '/' + el.id,
                name, lat: la, lng: ln,
                dist: haversine({ lat, lng }, { lat: la, lng: ln }),
                ...meta,
            });
        }
        zones.sort((a, b) => a.dist - b.dist);
        return zones;
    }

    function ensureMap(lat, lng) {
        if (mapInstance) return mapInstance;
        mapInstance = L.map('leafletMap', { zoomControl: true }).setView([lat, lng], 15);
        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
        }).addTo(mapInstance);
        zoneLayer = L.layerGroup().addTo(mapInstance);
        return mapInstance;
    }

    function setUserMarker(lat, lng) {
        const icon = L.divIcon({
            html: '<div class="you-marker"><div class="you-ping"></div><div class="you-core"></div></div>',
            className: '', iconSize: [14, 14], iconAnchor: [7, 7],
        });
        if (userMarker) userMarker.setLatLng([lat, lng]);
        else userMarker = L.marker([lat, lng], { icon, zIndexOffset: 1000 }).addTo(mapInstance);
        userMarker.bindTooltip('You', { direction: 'top', offset: [0, -8] });
    }

    function renderZoneMarkers(zones) {
        zoneLayer.clearLayers();
        zones.slice(0, 40).forEach(z => {
            const icon = L.divIcon({
                html: `<div class="zone-marker ${z.cls}"><span class="material-icons">${z.icon}</span></div>`,
                className: '', iconSize: [30, 30], iconAnchor: [15, 15],
            });
            z.marker = L.marker([z.lat, z.lng], { icon }).addTo(zoneLayer)
                .bindTooltip(`${escapeHtml(z.name)} · ${fmtDist(z.dist)}`, { direction: 'top', offset: [0, -14] });
            z.marker.on('click', () => focusZone(z));
        });
    }

    function renderZoneList(zones) {
        zoneList.innerHTML = '';
        if (!zones.length) {
            zoneList.innerHTML = '<div class="text-[11px] text-cyberOrange font-mono px-1 py-2">No safe zones found within 3 km on OpenStreetMap.</div>';
            return;
        }
        zones.slice(0, 12).forEach((z, i) => {
            const row = document.createElement('button');
            row.className = 'zone-row' + (i === 0 ? ' is-target' : '');
            row.dataset.zoneId = z.id;
            row.innerHTML =
                `<span class="zone-ico" style="background:${z.color}22;color:${z.color}"><span class="material-icons">${z.icon}</span></span>` +
                `<span class="flex-1 min-w-0">` +
                    `<span class="block text-[12px] font-semibold text-iceWhite truncate">${escapeHtml(z.name)}</span>` +
                    `<span class="block text-[9px] font-mono text-mutedSlate uppercase tracking-wide">${z.cat}</span>` +
                `</span>` +
                `<span class="text-right shrink-0">` +
                    `<span class="block text-[12px] font-mono font-bold" style="color:${z.color}">${fmtDist(z.dist)}</span>` +
                    `<span class="block text-[9px] font-mono text-mutedSlate">${fmtEtaWalk(z.dist)}</span>` +
                `</span>`;
            row.addEventListener('click', () => focusZone(z));
            zoneList.appendChild(row);
        });
    }

    async function routeTo(userLoc, z) {
        if (!userLoc || !z || !mapInstance) return;
        if (routeLayer) { mapInstance.removeLayer(routeLayer); routeLayer = null; }

        let coords = [[userLoc.lat, userLoc.lng], [z.lat, z.lng]];
        let etaSec = null, real = false;
        try {
            const url = `https://router.project-osrm.org/route/v1/driving/` +
                `${userLoc.lng},${userLoc.lat};${z.lng},${z.lat}?overview=full&geometries=geojson`;
            const res = await fetch(url);
            if (res.ok) {
                const route = (await res.json()).routes?.[0];
                if (route) {
                    coords = route.geometry.coordinates.map(c => [c[1], c[0]]);
                    etaSec = route.duration;
                    real = true;
                }
            }
        } catch (e) { /* fall back to a direct line */ }

        routeLayer = L.polyline(coords, {
            color: '#00E676', weight: 4, opacity: 0.9,
            dashArray: real ? null : '6 8', className: 'leaflet-routeline',
        }).addTo(mapInstance);
        try { mapInstance.fitBounds(routeLayer.getBounds().pad(0.25)); } catch (e) { /* noop */ }

        mapEta.textContent = etaSec != null ? fmtEtaDrive(etaSec) : fmtEtaWalk(z.dist);
        mapEtaWrap.classList.remove('hidden');
        mapHudLabel.textContent = real ? 'ROUTE · DRIVING' : 'ROUTE · DIRECT LINE';
    }

    async function focusZone(z) {
        currentZones.forEach(o => o.marker?.getElement()
            ?.querySelector('.zone-marker')?.classList.toggle('is-target', o === z));
        document.querySelectorAll('.zone-row').forEach(r =>
            r.classList.toggle('is-target', r.dataset.zoneId === z.id));
        mapZoneName.textContent = z.name;
        await routeTo(currentUserLoc, z);
    }

    // Main entry — called when a Code Red opens the active view.
    async function initMap() {
        mapZoneName.textContent = 'Locating…';
        mapEtaWrap.classList.add('hidden');
        mapHudLabel.textContent = 'LOCATING…';

        if (typeof L === 'undefined') {
            zoneList.innerHTML = '<div class="text-[11px] text-cyberOrange font-mono px-1 py-2">Map library unavailable (offline?). Safe-zone lookup skipped.</div>';
            mapHudLabel.textContent = 'MAP OFFLINE';
            return;
        }

        const loc = await getUserLocation();
        currentUserLoc = loc;
        ensureMap(loc.lat, loc.lng);
        setTimeout(() => { try { mapInstance.invalidateSize(); } catch (e) { /* noop */ } }, 80);
        setUserMarker(loc.lat, loc.lng);
        mapInstance.setView([loc.lat, loc.lng], 15);
        mapHudLabel.textContent = loc.approx ? 'LOCATION · APPROX' : 'LIVE LOCATION';
        if (loc.approx) {
            logEvent('System', 'Precise GPS unavailable — using approx Marathahalli area.', 'cyberOrange', 'my_location');
        }

        zoneList.innerHTML = '<div class="text-[11px] text-mutedSlate font-mono px-1 py-2">Scanning OpenStreetMap for nearby safe zones…</div>';
        try {
            const zones = await fetchSafeZones(loc.lat, loc.lng);
            currentZones = zones;
            renderZoneMarkers(zones);
            renderZoneList(zones);
            zoneSource.textContent = `OpenStreetMap · ${zones.length} found · live`;
            if (zones.length) {
                const target = zones.find(z => z.cls === 'z-police') || zones[0];
                mapZoneName.textContent = target.name;
                await focusZone(target);
            } else {
                mapZoneName.textContent = 'None within 3 km';
            }
        } catch (e) {
            const msg = escapeHtml(String(e && e.message ? e.message : e));
            zoneList.innerHTML = `<div class="text-[11px] text-crimsonRed font-mono px-1 py-2">Safe-zone lookup failed: ${msg}. Check your connection.</div>`;
            zoneSource.textContent = 'OpenStreetMap · error';
            mapHudLabel.textContent = 'LOOKUP FAILED';
        }
    }

    // Called when the backend streams "Route locked to <zone>". Match it to a
    // real OSM zone if we found one; otherwise just label the HUD (no bluffing).
    function setSafeZone(name, eta) {
        if (!name) return;
        const n = name.toLowerCase();
        const match = currentZones.find(z =>
            z.name.toLowerCase().includes(n) || n.includes(z.name.toLowerCase()));
        if (match) { focusZone(match); return; }
        mapZoneName.textContent = name;
        if (eta) { mapEta.textContent = eta; mapEtaWrap.classList.remove('hidden'); }
    }
    function drawRoute() { /* real route drawn by focusZone/routeTo; kept for call-site compatibility */ }

    // Parse live signals out of streamed messages (no new SSE fields needed)
    function parseSignals(agent, message) {
        const t = message.match(/threat\s+(HIGH|MEDIUM|LOW)\s*\((\d+)%/i);
        if (t) applyThreat(t[1].toUpperCase(), t[2]);

        const r = message.match(/Route locked to\s+([^·.]+)/i);
        if (r) {
            const eta = (message.match(/ETA\s+([^.·]+)/i) || [])[1];
            setSafeZone(r[1].trim(), eta ? eta.trim() : null);
            drawRoute();
        }
    }

    // Capture a short ambient mic clip so Omni can do REAL voice analysis.
    // Resolves to { dataUrl, mime } or null if the mic is denied/unavailable.
    async function captureMicClip(ms = 3500) {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia ||
            typeof MediaRecorder === 'undefined') {
            return null;
        }
        let stream;
        try {
            stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        } catch (e) {
            console.log('Mic unavailable / denied:', e && e.name);
            return null;
        }
        return new Promise((resolve) => {
            let mime = '';
            const prefs = ['audio/webm;codecs=opus', 'audio/webm',
                           'audio/ogg;codecs=opus', 'audio/mp4'];
            for (const m of prefs) {
                if (MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(m)) {
                    mime = m; break;
                }
            }
            let rec;
            try {
                rec = mime ? new MediaRecorder(stream, { mimeType: mime })
                           : new MediaRecorder(stream);
            } catch (e) {
                stream.getTracks().forEach(t => t.stop());
                resolve(null);
                return;
            }
            const chunks = [];
            rec.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
            rec.onstop = () => {
                stream.getTracks().forEach(t => t.stop());
                const type = rec.mimeType || mime || 'audio/webm';
                const blob = new Blob(chunks, { type });
                const reader = new FileReader();
                reader.onloadend = () => resolve({
                    dataUrl: reader.result,          // "data:audio/webm;...;base64,…"
                    mime: type.split(';')[0],        // "audio/webm"
                });
                reader.onerror = () => resolve(null);
                reader.readAsDataURL(blob);
            };
            rec.start();
            setTimeout(() => { if (rec.state !== 'inactive') rec.stop(); }, ms);
        });
    }

    async function triggerCodeRed() {
        isDefenseActive = true;
        swapViews(hubView, activeView);

        eventLog.innerHTML = '';
        buildRoster();
        startTimer();
        initMap();
        setThreat(8, 'ASSESSING…');

        logEvent('System', 'Code Red Triggered.', isOffline ? 'cyberOrange' : 'crimsonRed');

        if (isOffline) activateDarkSurvival();
        else activateGhostOperator();

        // Online (Ghost Operator) only: grab a few seconds of ambient audio for
        // Omni's real voice analysis. Offline has no network for Gemini, so skip.
        let clip = null;
        if (!isOffline) {
            logEvent('Omni', 'Listening to ambient audio for voice analysis…', 'cyberOrange', 'mic');
            clip = await captureMicClip(3500);
            if (clip) {
                logEvent('Omni', 'Ambient audio captured — sending for voice analysis.', 'cyberOrange', 'graphic_eq');
            } else {
                logEvent('Omni', 'Mic unavailable — falling back to location analysis.', 'mutedSlate', 'mic_off');
            }
        }

        try {
            await fetch('/trigger', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    action: "code_red",
                    source: "button",
                    // Tell the backend which brain to use. "offline" forces the
                    // on-device Gemma path (DARK SURVIVAL); "online" lets the
                    // backend auto-fall to Gemma if Wi-Fi is actually down.
                    mode: isOffline ? "offline" : "online",
                    // Real ambient clip for Omni's voice analysis (may be absent).
                    audio: clip ? clip.dataUrl : undefined,
                    audio_mime: clip ? clip.mime : undefined
                })
            });
            startSSE();
        } catch (e) {
            console.log("Backend offline, running local mock.");
            runMockSequence();
        }
    }

    function resolveIncident() {
        isDefenseActive = false;
        clearInterval(timerHandle);
        setThreat(4, 'CLEARED');
        threatLabel.className = 'text-[10px] font-mono font-bold text-safetyGreen';

        stopSiren();
        if (sseSource) { sseSource.close(); sseSource = null; }
        appBody.classList.remove('dead-screen');
        deadScreenHint.classList.add('hidden');

        swapViews(activeView, hubView);

        fetch('/trigger', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: "resolve" })
        }).catch(() => {});
    }

    function activateDarkSurvival() {
        activeTitle.textContent = "DARK SURVIVAL ENGAGED";
        activeSubtitle.textContent = "ON-DEVICE GEMMA 4 ACTIVE";
        appBody.classList.add('dead-screen');
        deadScreenHint.classList.remove('hidden');
        resetDarkStatus();
        logEvent('Gemma', 'SOS Beacon QUEUED. Will transmit when signal returns.', 'cyberOrange', 'hourglass_top');
        startSiren();
    }

    // --- On-device Gemma status pill (visible on the dead screen) ---
    function resetDarkStatus() {
        gemmaInferences = 0;
        dsCount.textContent = '0';
        dsModel.textContent = 'connecting…';
        dsActivity.textContent = 'Booting on-device brain — no data leaves this phone';
    }

    // Feed every Gemma SSE event into the pill: track the model, count real
    // inferences, and show the latest on-device activity live.
    function updateDarkStatus(message) {
        // Model id arrives inside brackets, e.g. "... [google/gemma-4-e4b]"
        const m = message.match(/\[([^\]]+)\]/);
        if (m) dsModel.textContent = m[1];
        else if (/on-device model online/i.test(message)) dsModel.textContent = 'online';

        // Count actual model work (drafting / assessment), not status chatter.
        if (/(drafting|assessment|threat|drafted|assessing)/i.test(message)) {
            gemmaInferences++;
            dsCount.textContent = String(gemmaInferences);
        }

        // Latest activity line (strip the trailing "[model]" tag for brevity).
        dsActivity.textContent = message.replace(/\s*\[[^\]]+\]\s*$/, '');

        // Flash the pill so the offline work is visibly "pulsing".
        dsPill.classList.add('ds-flash');
        setTimeout(() => dsPill.classList.remove('ds-flash'), 350);
    }

    function activateGhostOperator() {
        activeTitle.textContent = "GHOST OPERATOR ENGAGED";
        activeSubtitle.textContent = "AUTONOMOUS ORCHESTRATION";
        appBody.classList.remove('dead-screen');
        deadScreenHint.classList.add('hidden');
        stopSiren();
    }

    function startSiren() {
        if (!audioContext) audioContext = new (window.AudioContext || window.webkitAudioContext)();
        if (oscillator) return;
        oscillator = audioContext.createOscillator();
        const gainNode = audioContext.createGain();
        oscillator.type = 'square';
        oscillator.frequency.setValueAtTime(800, audioContext.currentTime);
        setInterval(() => {
            if (oscillator && oscillator.frequency) {
                oscillator.frequency.setValueAtTime(800, audioContext.currentTime);
                oscillator.frequency.linearRampToValueAtTime(1200, audioContext.currentTime + 0.4);
            }
        }, 800);
        gainNode.gain.setValueAtTime(0.1, audioContext.currentTime);
        oscillator.connect(gainNode);
        gainNode.connect(audioContext.destination);
        oscillator.start();
    }

    function stopSiren() {
        if (oscillator) {
            oscillator.stop();
            oscillator.disconnect();
            oscillator = null;
        }
    }

    // Streamed log entry with timestamp, agent avatar + typing effect
    function logEvent(agent, message, colorClass = 'iceWhite', icon = null) {
        const meta = AGENTS[agent] || {};
        const useIcon = icon || meta.icon || 'shield';
        pingRoster(agent);
        parseSignals(agent, message);

        const iconColor = colorClass === 'crimsonRed' ? 'text-crimsonRed' :
                          colorClass === 'cyberOrange' ? 'text-cyberOrange' :
                          colorClass === 'safetyGreen' ? 'text-safetyGreen' : 'text-iceWhite';

        const ts = new Date().toLocaleTimeString('en-GB');

        const div = document.createElement('div');
        div.className = 'event-card flex items-start gap-3 bg-[#1A1F29]/80 p-3 rounded-lg border border-borderGray';
        div.innerHTML = `
            <span class="agent-avatar material-icons ${iconColor} text-sm mt-0.5 w-6 h-6 flex items-center justify-center rounded-md bg-surfaceGray border border-borderGray">${useIcon}</span>
            <div class="flex-1 min-w-0">
                <div class="flex items-center justify-between gap-2">
                    <span class="text-[10px] font-bold text-mutedSlate uppercase tracking-wide">${agent}</span>
                    <span class="text-[9px] text-mutedSlate/70 font-mono">${ts}</span>
                </div>
                <p class="text-xs text-${colorClass} mt-1 caret" data-full="${escapeAttr(message)}"></p>
            </div>
        `;
        eventLog.appendChild(div);
        eventLog.scrollTop = eventLog.scrollHeight;

        // Type the message out for a live "streaming" feel
        typeText(div.querySelector('p'), message);
    }

    function typeText(el, text) {
        let i = 0;
        const step = Math.max(1, Math.round(text.length / 26)); // ~26 frames total
        const iv = setInterval(() => {
            i += step;
            el.textContent = text.slice(0, i);
            eventLog.scrollTop = eventLog.scrollHeight;
            if (i >= text.length) {
                el.textContent = text;
                el.classList.remove('caret');
                clearInterval(iv);
            }
        }, 22);
    }

    function escapeAttr(s) {
        return String(s).replace(/"/g, '&quot;');
    }

    function startSSE() {
        if (sseSource) sseSource.close();
        sseSource = new EventSource('/session/1/events');
        sseSource.onmessage = (e) => {
            const data = JSON.parse(e.data);
            const meta = AGENTS[data.agent] || {};
            let color = meta.color || 'iceWhite';
            let icon = meta.icon || 'info';
            if (data.status === 'failed') color = 'crimsonRed';
            // Server flags the on-device path via mode:"offline". If Wi-Fi
            // dropped while the UI still showed GHOST OPERATOR, the backend
            // auto-fell to Gemma — mirror that in the UI (engage dark survival)
            // so the pill, siren and restore gestures all behave correctly.
            if (data.mode === 'offline' && !isOffline) {
                isOffline = true;
                modeToggle.checked = true;
                setOfflineMode(true);   // updates labels + net status + dead screen
            }
            if (data.agent === 'Gemma') updateDarkStatus(data.message);
            logEvent(data.agent, data.message, color, icon);
        };
    }

    // Mock Sequence for purely local demo
    function runMockSequence() {
        const events = [
            { agent: "Antigravity", message: "Spawning Action, Comms and Verification agents in parallel.", icon: "hub", color: "iceWhite", delay: 1000 },
            { agent: "Omni", message: "Threat HIGH (95% conf) — user isolated; autonomous response engaged.", icon: "info", color: "cyberOrange", delay: 2200 },
            { agent: "Computer Use", message: "Acquiring GPS lock. Opening Maps.", icon: "explore", color: "iceWhite", delay: 3200 },
            { agent: "Live Voice", message: "Calling emergency contact (Teammate SOS)...", icon: "phone_in_talk", color: "safetyGreen", delay: 4400 },
            { agent: "Computer Use", message: "Route locked to Cubbon Park Police Station · ETA 5 min. Live map ready.", icon: "directions_run", color: "iceWhite", delay: 6000 },
            { agent: "Live Voice", message: "Contact answered. Patching in live microphone...", icon: "record_voice_over", color: "safetyGreen", delay: 7600 },
            { agent: "Antigravity", message: "Response coordinated — route locked, contacts alerted. Standing by, monitoring.", icon: "hub", color: "iceWhite", delay: 9000 },
            { agent: "Omni", message: "Ambient re-scan — threat MEDIUM (55% conf) — de-escalating; approach underway.", icon: "info", color: "cyberOrange", delay: 12000 },
            { agent: "Omni", message: "Ambient re-scan — threat LOW (32% conf) — nearing safe zone. Contact en route.", icon: "info", color: "safetyGreen", delay: 16000 }
        ];
        events.forEach(ev => {
            setTimeout(() => {
                if (isDefenseActive && !isOffline) logEvent(ev.agent, ev.message, ev.color, ev.icon);
            }, ev.delay);
        });
    }
});
