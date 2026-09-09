'use strict';
// Sample states only. No sensor input, gambling, network calls or score persistence.
const table = document.querySelector('.table');
const home = table.querySelector('.home-screen');
const round = table.querySelector('.round-screen');
const dialog = table.querySelector('.dialog');
const decks = {
  wild: { title: 'Wild World', icon: '#wild-icon', cards: ['GIRAFFE', 'RAINBOW', 'MOUNTAIN GOAT', 'VENUS FLYTRAP'] },
  everyday: { title: 'Everyday Things', icon: '#everyday-icon', cards: ['WAFFLE', 'WASHING MACHINE', 'HOT AIR BALLOON', 'SAXOPHONE'] },
  actions: { title: 'Do Your Thing', icon: '#actions-icon', cards: ['WALKING A DOG', 'MISSING THE BUS', 'PLAYING THE GUITAR', 'BAKING A CAKE'] },
};
const state = { deck: 'wild', duration: 60, score: 4, card: 0, view: 'decks', pending: null, returnFocus: null };

function fit() {
  const width = table.parentElement.clientWidth;
  if (width) table.style.transform = `scale(${width / 896})`;
  const word = table.querySelector('.word');
  if (!round.hidden && !word.hidden) {
    let size = 67;
    word.style.fontSize = `${size}px`;
    while ((word.scrollWidth > word.clientWidth || word.scrollHeight > word.parentElement.clientHeight) && size > 26) {
      size -= 2;
      word.style.fontSize = `${size}px`;
    }
  }
}

function closeDialog(restore = true) {
  dialog.hidden = true;
  dialog.replaceChildren();
  dialog.removeAttribute('role');
  dialog.removeAttribute('aria-modal');
  home.inert = false;
  round.inert = false;
  if (restore && state.returnFocus?.isConnected) state.returnFocus.focus();
}

function showView(view) {
  clearTimeout(state.pending);
  state.pending = null;
  closeDialog(false);
  state.view = view;
  home.hidden = view !== 'decks';
  round.hidden = view === 'decks';
  const feedback = ['correct', 'pass'].includes(view);
  if (feedback) table.dataset.feedback = view; else delete table.dataset.feedback;
  table.querySelector('.word').hidden = feedback;
  table.querySelector('.feedback').hidden = !feedback;
  table.querySelector('.feedback strong').textContent = view === 'pass' ? 'Pass' : 'Correct';
  table.querySelector('.feedback-icon use').setAttribute('href', view === 'pass' ? '#pass' : '#check');
  const deck = decks[state.deck];
  table.querySelector('.word').textContent = deck.cards[state.card % deck.cards.length];
  table.querySelector('.round-deck').textContent = deck.title;
  table.querySelectorAll('.active-icon').forEach(icon => icon.setAttribute('href', deck.icon));
  const score = table.querySelector('.card-score');
  score.querySelector('span').textContent = String(state.score);
  score.setAttribute('aria-label', `${state.score} correct`);
  const remaining = Math.min(42, state.duration);
  table.querySelector('.card-time').textContent = `${remaining}s`;
  table.querySelector('.card-time').setAttribute('aria-label', `${remaining} seconds`);
  table.querySelectorAll('[data-duration]').forEach(button => button.setAttribute('aria-pressed', String(Number(button.dataset.duration) === state.duration)));
  document.querySelectorAll('[data-view]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.view === view)));
  table.querySelectorAll('.play-controls button').forEach(button => { button.disabled = feedback; });
  requestAnimationFrame(fit);
}

function showDialog(type, trigger) {
  // Opening pause during the short feedback state must not strand disabled controls.
  if (['correct', 'pass'].includes(state.view)) showView('round');
  clearTimeout(state.pending);
  state.pending = null;
  const content = {
    help: ['How to play', 'Hold at your forehead. Friends give clues. Down for correct; up to pass. Return to your starting angle.'],
    settings: ['Settings', 'The Android version keeps sound, vibration, gentle tilts and touch controls.'],
    history: ['Recent rounds', 'Saved rounds stay in the app. This prototype does not save scores.'],
    pause: ['Paused', ''],
  }[type];
  dialog.replaceChildren();
  const heading = document.createElement('h3'); heading.id = 'dialog-title'; heading.textContent = content[0]; dialog.append(heading);
  if (content[1]) { const text = document.createElement('p'); text.textContent = content[1]; dialog.append(text); }
  const button = document.createElement('button'); button.className = 'press green-button'; button.textContent = type === 'pause' ? 'Resume' : 'Back'; button.addEventListener('click', () => closeDialog()); dialog.append(button);
  const note = document.createElement('p'); note.className = 'preview-notice'; note.textContent = 'Visual prototype'; dialog.append(note);
  state.returnFocus = trigger;
  home.inert = true; round.inert = true;
  dialog.setAttribute('role', 'dialog'); dialog.setAttribute('aria-modal', 'true'); dialog.setAttribute('aria-labelledby', 'dialog-title'); dialog.hidden = false;
  button.focus();
}

document.addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!button || button.closest('.dialog')) return;
  if (button.dataset.view) { showView(button.dataset.view); return; }
  if (button.dataset.deck) {
    state.deck = button.dataset.deck; state.card = 0; state.score = 4;
    showView('round'); table.querySelector('[data-action=correct]').focus(); return;
  }
  if (button.dataset.duration) { state.duration = Number(button.dataset.duration); showView('decks'); return; }
  const action = button.dataset.action;
  if (action === 'decks') { showView('decks'); table.querySelector(`[data-deck=${state.deck}]`).focus(); }
  else if (['help', 'settings', 'history', 'pause'].includes(action)) showDialog(action, button);
  else if (action === 'correct' || action === 'pass') {
    if (state.view !== 'round') return;
    if (action === 'correct') state.score++;
    state.card++;
    showView(action);
    document.getElementById('announcement').textContent = `${action === 'correct' ? 'Correct' : 'Pass'}. Sample score ${state.score}.`;
    state.pending = setTimeout(() => showView('round'), 650);
  }
});
document.addEventListener('keydown', event => {
  if (dialog.hidden) return;
  if (event.key === 'Escape') { event.preventDefault(); closeDialog(); }
  if (event.key === 'Tab') { event.preventDefault(); dialog.querySelector('button').focus(); }
});
const params = new URLSearchParams(location.search);
if (params.has('isolate')) document.body.dataset.isolate = 'true';
if (Object.hasOwn(decks, params.get('deck'))) state.deck = params.get('deck');
showView(['round', 'correct', 'pass'].includes(params.get('view')) ? params.get('view') : 'decks');
new ResizeObserver(fit).observe(document.body);
window.addEventListener('resize', fit);
document.fonts.ready.then(fit);