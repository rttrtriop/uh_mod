// ==UserScript==
// @name         UH Mod Assistant Core Engine
// @version      0.0.3
// @description  Автономный движок решения тестов и интеграции сервисов приложения «Учусь в Кузбассе»
// @author       ONIM (@onimosik)
// ==/UserScript==

(function () {
    'use strict';

    if (window.UH_MOD_INITIALIZED) {
        console.log('[UH_MOD] Core already active, skipping re-init.');
        return;
    }
    window.UH_MOD_INITIALIZED = true;
    window.UH_MOD_ACTIVE = true;
    window.UH_MOD_VERSION = '0.0.3';

    console.log('[UH_MOD] Core Engine v0.0.3 starting up...');

    const CONFIG = {
        backendUrl: 'https://uh-mod.onrender.com',
        hintsEnabled: true,
        highlightColor: '#00E676',
        highlightBg: 'rgba(0, 230, 118, 0.18)',
        badgeColor: '#7C4DFF',
        autoSolveDelay: 400
    };

    const MOD = {
        config: CONFIG,

        // 1. Поиск и извлечение вопросов на странице теста
        extractQuestions: function () {
            const questions = [];
            const qElements = document.querySelectorAll('.question-block, .test-question, .task-card, .test_item, [data-question-id]');
            
            if (qElements.length > 0) {
                qElements.forEach((el, index) => {
                    const qId = el.getAttribute('data-question-id') || el.id || ('q_' + (index + 1));
                    const qTextEl = el.querySelector('.question-text, .task-text, .title, h3, h4, p') || el;
                    const qText = (qTextEl.innerText || qTextEl.textContent || '').trim();
                    
                    const options = [];
                    const optElements = el.querySelectorAll('.option, .answer-variant, label, .radio-label, .checkbox-label');
                    optElements.forEach(opt => {
                        const optText = (opt.innerText || opt.textContent || '').trim();
                        if (optText) options.push(optText);
                    });

                    questions.push({
                        id: qId,
                        type: options.length > 0 ? 'choice' : 'input',
                        question: qText,
                        options: options,
                        element: el
                    });
                });
            } else {
                // Одиночный вопрос на странице
                const mainQuestion = document.querySelector('h1, h2, .question, .task') || document.body;
                const options = [];
                document.querySelectorAll('input[type="radio"], input[type="checkbox"], label').forEach(opt => {
                    const text = (opt.innerText || opt.textContent || opt.value || '').trim();
                    if (text) options.push(text);
                });
                if (options.length > 0) {
                    questions.push({
                        id: 'single_q',
                        type: 'choice',
                        question: (mainQuestion.innerText || '').trim(),
                        options: options,
                        element: document.body
                    });
                }
            }
            return questions;
        },

        // 2. Локальный парсер (мгновенное решение математических и логических вопросов)
        localSolve: function (qText, options) {
            if (!qText) return null;
            const text = qText.toLowerCase();

            // Линейные уравнения вида ax + b = c
            const eqMatch = text.match(/([+-]?\s*\d*)\s*x\s*([+-]\s*\d+)?\s*=\s*([+-]?\s*\d+)/i);
            if (eqMatch) {
                let aStr = (eqMatch[1] || '').replace(/\s+/g, '');
                let bStr = (eqMatch[2] || '').replace(/\s+/g, '');
                let cStr = (eqMatch[3] || '').replace(/\s+/g, '');

                let a = (aStr === '' || aStr === '+') ? 1 : (aStr === '-' ? -1 : parseFloat(aStr));
                let b = bStr ? parseFloat(bStr) : 0;
                let c = parseFloat(cStr);

                if (!isNaN(a) && a !== 0 && !isNaN(b) && !isNaN(c)) {
                    const ans = (c - b) / a;
                    return { answer: String(ans), type: 'math' };
                }
            }

            // Квадратный корень: sqrt(144) или корень из 144
            const sqrtMatch = text.match(/(?:sqrt|корень\s*(?:из)?)\s*\(?(\d+)\)?/i);
            if (sqrtMatch) {
                const num = parseFloat(sqrtMatch[1]);
                if (!isNaN(num)) {
                    return { answer: String(Math.sqrt(num)), type: 'math' };
                }
            }

            // Арифметика: a + b, a - b, a * b, a / b
            const arithMatch = text.match(/(\d+(?:\.\d+)?)\s*([\+\-\*\/])\s*(\d+(?:\.\d+)?)/);
            if (arithMatch) {
                const n1 = parseFloat(arithMatch[1]);
                const op = arithMatch[2];
                const n2 = parseFloat(arithMatch[3]);
                let res = 0;
                if (op === '+') res = n1 + n2;
                else if (op === '-') res = n1 - n2;
                else if (op === '*') res = n1 * n2;
                else if (op === '/' && n2 !== 0) res = n1 / n2;
                return { answer: String(res), type: 'math' };
            }

            return null;
        },

        // 3. Подсветка ответов в интерфейсе
        applyAnswers: function (answersMap) {
            if (!answersMap || typeof answersMap !== 'object') return;
            console.log('[UH_MOD] Highlighting correct answers:', answersMap);

            const allClickables = document.querySelectorAll('label, .option, .answer, .variant, input[type="radio"], input[type="checkbox"], [role="radio"]');

            allClickables.forEach(node => {
                const nodeText = (node.innerText || node.textContent || node.value || '').trim().toLowerCase();
                if (!nodeText) return;

                for (const [qid, ansData] of Object.entries(answersMap)) {
                    const targetText = String(ansData.answer || ansData.answer_text || '').trim().toLowerCase();
                    if (!targetText) continue;

                    if (nodeText.includes(targetText) || targetText.includes(nodeText)) {
                        node.style.transition = 'all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1)';
                        node.style.border = '2px solid ' + CONFIG.highlightColor;
                        node.style.borderRadius = '12px';
                        node.style.backgroundColor = CONFIG.highlightBg;
                        node.style.boxShadow = '0 0 12px rgba(0, 230, 118, 0.35)';

                        // Добавляем бейдж «✓ ONIM AI»
                        if (!node.querySelector('.uh-mod-badge')) {
                            const badge = document.createElement('span');
                            badge.className = 'uh-mod-badge';
                            badge.innerText = ' ✓ AI ';
                            badge.style.display = 'inline-block';
                            badge.style.marginLeft = '8px';
                            badge.style.padding = '2px 8px';
                            badge.style.borderRadius = '8px';
                            badge.style.background = 'linear-gradient(135deg, #7C4DFF, #00E676)';
                            badge.style.color = '#FFFFFF';
                            badge.style.fontSize = '11px';
                            badge.style.fontWeight = 'bold';
                            node.appendChild(badge);
                        }
                    }
                }
            });
        },

        // 4. Запрос к AI бэкенду Gemini
        requestAiSolve: async function (questions) {
            if (!questions || questions.length === 0) return;
            const payloadQuestions = questions.map(q => ({
                id: q.id,
                type: q.type,
                question: q.question,
                options: q.options
            }));

            try {
                const response = await fetch(CONFIG.backendUrl + '/solve_batch', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        test_id: 'auto_' + Date.now(),
                        questions: payloadQuestions
                    })
                });

                if (response.ok) {
                    const data = await response.json();
                    if (data && data.answers) {
                        const answersMap = {};
                        data.answers.forEach(a => {
                            answersMap[String(a.question_id)] = a;
                        });
                        MOD.applyAnswers(answersMap);
                    }
                } else {
                    console.warn('[UH_MOD] AI backend response error:', response.status);
                }
            } catch (err) {
                console.error('[UH_MOD] AI backend fetch failed:', err);
            }
        },

        // 5. Главный цикл инициализации
        run: function () {
            if (!CONFIG.hintsEnabled) {
                console.log('[UH_MOD] Hints disabled by user settings.');
                return;
            }

            const questions = MOD.extractQuestions();
            console.log('[UH_MOD] Discovered questions:', questions.length);

            if (questions.length === 0) return;

            // Локальный мгновенный прогон
            const localAnswers = {};
            const needAi = [];

            questions.forEach(q => {
                const solved = MOD.localSolve(q.question, q.options);
                if (solved) {
                    localAnswers[q.id] = { answer: solved.answer };
                } else {
                    needAi.push(q);
                }
            });

            if (Object.keys(localAnswers).length > 0) {
                MOD.applyAnswers(localAnswers);
            }

            if (needAi.length > 0) {
                MOD.requestAiSolve(needAi);
            }
        }
    };

    window.UH_MOD = MOD;

    // Автоматический запуск при загрузке документа
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => setTimeout(MOD.run, CONFIG.autoSolveDelay));
    } else {
        setTimeout(MOD.run, CONFIG.autoSolveDelay);
    }
})();
