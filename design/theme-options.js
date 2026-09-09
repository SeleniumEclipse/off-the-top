'use strict';
// Visual prototypes only: sample cards, fixed preview time, no persistence or sensor input.
const concepts = [...document.querySelectorAll('.concept')];
const screens = [...document.querySelectorAll('.screen')];
const cards = ['GIRAFFE', 'WASHING MACHINE', 'WALKING A DOG', 'LIGHTHOUSE', 'HOT AIR BALLOON'];
const state = new Map(screens.map(screen => [screen, { deck: 'Wild World', score: 4, card: 0, duration: 60, round: false, feedbackTimer: null }]));
let selectedFilter = 'all';
let selectedScreen = 'decks';
let lastModalTrigger = null;

function fitScreens() {
  screens.forEach(screen => {
    const width = screen.parentElement.clientWidth;
    if (width) screen.style.transform = `scale(${width / 896})`;
  });
  document.querySelectorAll('.word').forEach(word => {
    if (!word.closest('.round-screen').hidden) {
      const limit = word.closest('.scoreboard-theme') ? 65 : word.closest('.tickets-theme') ? 67 : 72;
      word.style.fontSize = `${limit}px`;
      let size = limit;
      while ((word.scrollWidth > word.clientWidth || word.scrollHeight > word.parentElement.clientHeight - 30) && size > 28) {
        size -= 2;
        word.style.fontSize = `${size}px`;
      }
    }
  });
}

function renderScreen(screen, round) {
  const s = state.get(screen);
  s.round = round;
  screen.querySelector('.deck-screen').hidden = round;
  screen.querySelector('.round-screen').hidden = !round;
  screen.querySelectorAll('.rail-deck').forEach(el => { el.textContent = s.deck; });
  screen.querySelectorAll('.word').forEach(el => { el.textContent = cards[s.card % cards.length]; });
  screen.querySelectorAll('.score').forEach(el => { el.textContent = s.score; });
  const digits = screen.querySelector('.score-digits');
  if (digits) { digits.innerHTML = String(s.score).padStart(2, '0').split('').map(d => `<i>${d}</i>`).join(''); digits.setAttribute('aria-label', `${s.score} correct`); }
  const remaining = Math.min(42, s.duration);
  screen.querySelectorAll('.time').forEach(el => { el.textContent = `${remaining}s`; });
  const timer = screen.querySelector('.timer-digits');
  if (timer) { timer.innerHTML = String(remaining).padStart(2, '0').split('').map(d => `<i>${d}</i>`).join(''); timer.setAttribute('aria-label', `${remaining} seconds`); }
  screen.querySelectorAll('[data-duration]').forEach(button => button.setAttribute('aria-pressed', String(Number(button.dataset.duration) === s.duration)));
  closeOverlay(screen, false);
  requestAnimationFrame(fitScreens);
}

function chooseScreen(which) {
  selectedScreen = which;
  screens.forEach(screen => renderScreen(screen, which === 'round'));
  document.querySelectorAll('[data-screen]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.screen === which)));
  document.getElementById('preview-announcement').textContent = which === 'round' ? 'Showing active round previews' : 'Showing deck selection previews';
}

function chooseFilter(filter) {
  selectedFilter = filter;
  concepts.forEach(concept => { concept.hidden = filter !== 'all' && concept.dataset.concept !== filter; });
  document.querySelectorAll('[data-filter]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.filter === filter)));
  requestAnimationFrame(fitScreens);
}

function closeOverlay(screen, restoreFocus = true) {
  const overlay = screen.querySelector('.overlay');
  if (overlay.hidden) return;
  overlay.hidden = true;
  overlay.replaceChildren();
  overlay.removeAttribute('role');
  overlay.removeAttribute('aria-modal');
  screen.querySelector('.deck-screen').inert = false;
  screen.querySelector('.round-screen').inert = false;
  if (restoreFocus && lastModalTrigger?.isConnected) lastModalTrigger.focus();
}

function showOverlay(screen, type, trigger) {
  const overlay = screen.querySelector('.overlay');
  const content = {
    help: ['How to play', 'Hold the phone at your forehead. Friends give clues. Tilt down for correct, up to pass.'],
    settings: ['Settings', 'The final app keeps sound, vibration, gentle tilts, and touch controls.'],
    history: ['Recent rounds', 'Your saved rounds would appear here.'],
    pause: ['Paused', ''],
  }[type];
  overlay.replaceChildren();
  const heading = document.createElement('h3');
  heading.id = `${screen.dataset.theme}-dialog-title`;
  heading.textContent = content[0];
  overlay.append(heading);
  if (content[1]) { const text = document.createElement('p'); text.textContent = content[1]; overlay.append(text); }
  const actions = document.createElement('div'); actions.className = 'dialog-actions';
  const close = document.createElement('button'); close.className = 'physical primary-button'; close.textContent = type === 'pause' ? 'Resume' : 'Back'; close.addEventListener('click', () => closeOverlay(screen)); actions.append(close);
  if (type === 'pause') { const end = document.createElement('button'); end.className = 'physical'; end.textContent = 'Decks'; end.addEventListener('click', () => { closeOverlay(screen, false); renderScreen(screen, false); screen.querySelector('[data-deck]').focus(); }); actions.append(end); }
  overlay.append(actions);
  const note = document.createElement('p'); note.className = 'preview-only'; note.textContent = 'Visual preview — not connected to your game.'; overlay.append(note);
  lastModalTrigger = trigger;
  screen.querySelector('.deck-screen').inert = true;
  screen.querySelector('.round-screen').inert = true;
  overlay.setAttribute('role', 'dialog'); overlay.setAttribute('aria-modal', 'true'); overlay.setAttribute('aria-labelledby', heading.id);
  overlay.hidden = false;
  close.focus();
}

document.querySelectorAll('[data-filter]').forEach(button => button.addEventListener('click', () => chooseFilter(button.dataset.filter)));
document.querySelectorAll('[data-screen]').forEach(button => button.addEventListener('click', () => chooseScreen(button.dataset.screen)));
screens.forEach(screen => {
  screen.addEventListener('click', event => {
    const button = event.target.closest('button');
    if (!button || button.closest('.overlay')) return;
    const s = state.get(screen);
    if (button.dataset.deck) { s.deck = button.dataset.deck; s.score = 4; s.card = 0; renderScreen(screen, true); screen.querySelector('[data-action=pass]').focus(); }
    if (button.dataset.duration) { s.duration = Number(button.dataset.duration); renderScreen(screen, s.round); }
    const action = button.dataset.action;
    if (['help', 'settings', 'history', 'pause'].includes(action)) showOverlay(screen, action, button);
    if (action === 'back') { renderScreen(screen, false); screen.querySelector('[data-deck]').focus(); }
    if (action === 'correct' || action === 'pass') {
      if (action === 'correct') s.score++;
      s.card++;
      renderScreen(screen, true);
      screen.dataset.feedback = action;
      clearTimeout(s.feedbackTimer);
      s.feedbackTimer = setTimeout(() => { delete screen.dataset.feedback; }, 300);
      document.getElementById('preview-announcement').textContent = `${screen.getAttribute('aria-label')}: ${action}, sample score ${s.score}`;
    }
  });
});
document.addEventListener('keydown', event => {
  const overlay = [...document.querySelectorAll('.overlay')].find(el => !el.hidden);
  if (!overlay) return;
  if (event.key === 'Escape') { closeOverlay(overlay.closest('.screen')); event.preventDefault(); }
  if (event.key === 'Tab') {
    const buttons = [...overlay.querySelectorAll('button')];
    if (event.shiftKey && document.activeElement === buttons[0]) { buttons.at(-1).focus(); event.preventDefault(); }
    else if (!event.shiftKey && document.activeElement === buttons.at(-1)) { buttons[0].focus(); event.preventDefault(); }
  }
});
const params = new URLSearchParams(location.search);
if (params.has('isolate')) document.body.dataset.isolate = params.get('isolate');
chooseFilter(['table', 'scoreboard', 'tickets'].includes(params.get('theme')) ? params.get('theme') : 'all');
chooseScreen(params.get('screen') === 'round' ? 'round' : 'decks');
new ResizeObserver(fitScreens).observe(document.body);
window.addEventListener('resize', fitScreens);
document.fonts.ready.then(fitScreens);