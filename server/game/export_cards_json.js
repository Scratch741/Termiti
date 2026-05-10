'use strict';
/**
 * Exportuje ALL_CARDS jako JSON do stdout.
 * Spusť: node export_cards_json.js
 * nebo automaticky přes Gradle task "syncCards" při Android buildu.
 */
const { ALL_CARDS } = require('./cards.js');
process.stdout.write(JSON.stringify(ALL_CARDS, null, 2));
