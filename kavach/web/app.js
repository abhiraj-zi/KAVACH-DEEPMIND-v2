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
    const netDot = document.getElementById('netDot');
    const netStatus = document.getElementById('netStatus');
    const clockEl = document.getElementById('clock');
    const incidentTimer = document.getElementById('incidentTimer');
    const threatMeter = document.getElementById('threatMeter');
    const threatLabel = document.getElementById('threatLabel');
    const agentRoster = document.getElementById('agentRoster');

    // State
    let isOffline = false;
    let isDefenseActive = false;
    let sseSource = null;
    let audioContext = null;
    let oscillator = null;
    let timerHandle = null;
    let incidentStart = 0;

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

    async function triggerCodeRed() {
        isDefenseActive = true;
        swapViews(hubView, activeView);

        eventLog.innerHTML = '';
        buildRoster();
        startTimer();
        setThreat(8, 'ASSESSING…');
        setTimeout(() => setThreat(78, 'HIGH'), 1400);

        logEvent('System', 'Code Red Triggered.', isOffline ? 'cyberOrange' : 'crimsonRed');

        if (isOffline) activateDarkSurvival();
        else activateGhostOperator();

        try {
            await fetch('/trigger', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: "code_red", source: "button" })
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
        logEvent('Gemma', 'SOS Beacon QUEUED. Will transmit when signal returns.', 'cyberOrange', 'hourglass_top');
        startSiren();
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
            logEvent(data.agent, data.message, color, icon);
        };
    }

    // Mock Sequence for purely local demo
    function runMockSequence() {
        const events = [
            { agent: "Antigravity", message: "Spawning Action and Comms agents in parallel.", icon: "hub", color: "iceWhite", delay: 1000 },
            { agent: "Computer Use", message: "Acquiring GPS lock. Opening Maps.", icon: "explore", color: "iceWhite", delay: 2500 },
            { agent: "Live Voice", message: "Calling emergency contact (Teammate SOS)...", icon: "phone_in_talk", color: "safetyGreen", delay: 4000 },
            { agent: "Computer Use", message: "Routing to CVS 24/7 Pharmacy. ETA 4 mins.", icon: "directions_run", color: "iceWhite", delay: 5500 },
            { agent: "Omni", message: "Ambient audio assessed: elevated threat. Escalating.", icon: "info", color: "cyberOrange", delay: 7000 },
            { agent: "Live Voice", message: "Contact answered. Patching in live microphone...", icon: "record_voice_over", color: "safetyGreen", delay: 8500 }
        ];
        events.forEach(ev => {
            setTimeout(() => {
                if (isDefenseActive && !isOffline) logEvent(ev.agent, ev.message, ev.color, ev.icon);
            }, ev.delay);
        });
    }
});
