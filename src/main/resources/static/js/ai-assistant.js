/**
 * GharFix AI Assistant — Client Integration
 * Powered by Google Gemini via Spring Boot backend API.
 * 
 * Features:
 * - Natural language home-service diagnostic chat
 * - Multimodal image defect analysis (JPEG, PNG, WebP < 5MB)
 * - Grounded catalog cost estimates with disclaimers
 * - Human-in-the-loop action proposals (bookings, cancellations, refunds, complaints)
 * - Safe action execution with single-use confirmation tokens
 */
(function() {
    'use strict';

    // State
    const state = {
        isOpen: false,
        isThinking: false,
        history: [], // { role: 'user'|'assistant', content: string, timestamp: string }
        attachedImage: null, // { file: File, dataUrl: string, base64: string, mimeType: string }
        activeActionTokens: new Set()
    };

    const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

    // DOM Elements Cache
    let dom = {};

    function initElements() {
        dom = {
            launcher: document.getElementById('gfAiLauncher'),
            drawer: document.getElementById('gfAiDrawer'),
            backdrop: document.getElementById('gfAiBackdrop'),
            closeBtn: document.getElementById('gfAiCloseBtn'),
            minimizeBtn: document.getElementById('gfAiMinimizeBtn'),
            clearBtn: document.getElementById('gfAiClearBtn'),
            messages: document.getElementById('gfAiMessages'),
            form: document.getElementById('gfAiForm'),
            input: document.getElementById('gfAiInput'),
            sendBtn: document.getElementById('gfAiSendBtn'),
            attachBtn: document.getElementById('gfAiAttachBtn'),
            fileInput: document.getElementById('gfAiFileInput'),
            imgPreviewBar: document.getElementById('gfAiImagePreviewBar'),
            previewThumb: document.getElementById('gfAiPreviewThumb'),
            previewName: document.getElementById('gfAiPreviewName'),
            previewSize: document.getElementById('gfAiPreviewSize'),
            removeImgBtn: document.getElementById('gfAiRemoveImgBtn'),
            statusDot: document.getElementById('gfAiStatusDot'),
            statusText: document.getElementById('gfAiStatusText')
        };
    }

    // Initialize Widget
    function init() {
        initElements();
        if (!dom.launcher || !dom.drawer) return;

        attachEventListeners();
        checkBackendStatus();

        // Expose global helper for other page scripts
        window.openGharFixAI = function(prefilledPrompt, bookingId) {
            openDrawer();
            if (prefilledPrompt && dom.input) {
                dom.input.value = prefilledPrompt;
                autoResizeTextarea();
                if (bookingId) {
                    dom.input.setAttribute('data-booking-id', bookingId);
                }
                dom.input.focus();
            }
        };
    }

    function attachEventListeners() {
        dom.launcher.addEventListener('click', toggleDrawer);
        if (dom.closeBtn) dom.closeBtn.addEventListener('click', closeDrawer);
        if (dom.minimizeBtn) dom.minimizeBtn.addEventListener('click', closeDrawer);
        if (dom.backdrop) dom.backdrop.addEventListener('click', closeDrawer);

        if (dom.clearBtn) {
            dom.clearBtn.addEventListener('click', () => {
                state.history = [];
                clearAttachedImage();
                // Clear all messages except the welcome card
                const welcome = dom.messages.querySelector('.gf-ai-welcome-card');
                dom.messages.innerHTML = '';
                if (welcome) dom.messages.appendChild(welcome);
            });
        }

        // Quick prompt chips
        dom.drawer.querySelectorAll('.gf-ai-chip').forEach(chip => {
            chip.addEventListener('click', function() {
                const prompt = this.getAttribute('data-prompt') || this.textContent.trim();
                if (dom.input) {
                    dom.input.value = prompt;
                    sendMessage();
                }
            });
        });

        // Input form
        if (dom.form) {
            dom.form.addEventListener('submit', (e) => {
                e.preventDefault();
                sendMessage();
            });
        }

        if (dom.input) {
            dom.input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    sendMessage();
                }
            });
            dom.input.addEventListener('input', autoResizeTextarea);
        }

        // Image Attachment
        if (dom.attachBtn && dom.fileInput) {
            dom.attachBtn.addEventListener('click', () => dom.fileInput.click());
            dom.fileInput.addEventListener('change', handleFileSelected);
        }

        if (dom.removeImgBtn) {
            dom.removeImgBtn.addEventListener('click', clearAttachedImage);
        }

        // Escape to close
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && state.isOpen) {
                closeDrawer();
            }
        });
    }

    function toggleDrawer() {
        if (state.isOpen) closeDrawer();
        else openDrawer();
    }

    function openDrawer() {
        state.isOpen = true;
        dom.drawer.classList.add('active');
        if (dom.backdrop) dom.backdrop.classList.add('active');
        dom.launcher.setAttribute('aria-expanded', 'true');
        setTimeout(() => {
            if (dom.input) dom.input.focus();
            scrollToBottom();
        }, 150);
    }

    function closeDrawer() {
        state.isOpen = false;
        dom.drawer.classList.remove('active');
        if (dom.backdrop) dom.backdrop.classList.remove('active');
        dom.launcher.setAttribute('aria-expanded', 'false');
    }

    function autoResizeTextarea() {
        if (!dom.input) return;
        dom.input.style.height = 'auto';
        const newHeight = Math.min(dom.input.scrollHeight, 120);
        dom.input.style.height = (newHeight > 42 ? newHeight : 42) + 'px';
    }

    // Multimodal Image Handling
    function handleFileSelected(e) {
        const file = e.target.files && e.target.files[0];
        if (!file) return;

        // Reset input for repeat selections
        dom.fileInput.value = '';

        // Validate type
        if (!ALLOWED_MIME_TYPES.includes(file.type.toLowerCase())) {
            showToast('Unsupported image format. Please select a JPG, PNG, or WebP photo.', 'error');
            return;
        }

        // Validate size
        if (file.size > MAX_FILE_SIZE_BYTES) {
            showToast('Image is too large (' + (file.size / (1024 * 1024)).toFixed(1) + 'MB). Maximum size is 5MB.', 'error');
            return;
        }

        const reader = new FileReader();
        reader.onload = function(ev) {
            const dataUrl = ev.target.result;
            const base64Content = dataUrl.split(',')[1];
            state.attachedImage = {
                file: file,
                dataUrl: dataUrl,
                base64: base64Content,
                mimeType: file.type
            };

            // Show Preview Bar
            if (dom.previewThumb) dom.previewThumb.src = dataUrl;
            if (dom.previewName) dom.previewName.textContent = file.name;
            if (dom.previewSize) dom.previewSize.textContent = (file.size / 1024).toFixed(0) + ' KB';
            if (dom.imgPreviewBar) dom.imgPreviewBar.classList.add('active');

            if (dom.input && !dom.input.value.trim()) {
                dom.input.placeholder = 'Add a question or describe the photo...';
            }
        };
        reader.readAsDataURL(file);
    }

    function clearAttachedImage() {
        state.attachedImage = null;
        if (dom.imgPreviewBar) dom.imgPreviewBar.classList.remove('active');
        if (dom.previewThumb) dom.previewThumb.src = '';
        if (dom.input) dom.input.placeholder = 'Describe your home issue or ask a question...';
    }

    // Backend Availability Check
    async function checkBackendStatus() {
        try {
            const res = await fetch('/api/ai/status', { credentials: 'same-origin' });
            if (res.ok) {
                const data = await res.json();
                if (dom.statusDot) dom.statusDot.className = 'gf-ai-status-dot online';
                if (dom.statusText) dom.statusText.textContent = data.configured ? 'Gemini AI Online' : 'AI Assistant Ready';
            }
        } catch (e) {
            if (dom.statusDot) dom.statusDot.className = 'gf-ai-status-dot offline';
            if (dom.statusText) dom.statusText.textContent = 'GharFix Concierge';
        }
    }

    // Send Message Workflow
    async function sendMessage() {
        if (state.isThinking) return;

        const text = (dom.input.value || '').trim();
        const attachedImg = state.attachedImage;

        if (!text && !attachedImg) return;

        const bookingIdAttr = dom.input.getAttribute('data-booking-id');
        const bookingId = bookingIdAttr ? parseInt(bookingIdAttr, 10) : null;
        dom.input.removeAttribute('data-booking-id');

        // Render User Message
        appendUserMessage(text, attachedImg ? attachedImg.dataUrl : null);

        // Reset input UI
        dom.input.value = '';
        autoResizeTextarea();
        clearAttachedImage();

        // Show thinking indicator
        state.isThinking = true;
        setSendingState(true);
        const thinkingIndicator = appendThinkingIndicator();
        scrollToBottom();

        // Prepare Payload
        const payload = {
            message: text || (attachedImg ? 'Please analyze this photo of my household issue.' : 'Hello'),
            history: state.history.slice(-8), // Send recent context
            imageBase64: attachedImg ? attachedImg.base64 : null,
            imageMimeType: attachedImg ? attachedImg.mimeType : null,
            bookingId: bookingId
        };

        try {
            const response = await fetch('/api/ai/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                credentials: 'same-origin',
                body: JSON.stringify(payload)
            });

            removeThinkingIndicator(thinkingIndicator);

            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                const errMsg = errData.message || errData.error || 'Server returned status ' + response.status;
                appendErrorMessage('Sorry, I encountered an issue processing your request: ' + errMsg);
                return;
            }

            const data = await response.json();
            if (!data.success && data.error) {
                appendErrorMessage(data.error);
                return;
            }

            // Save in history
            state.history.push({ role: 'user', content: payload.message, timestamp: new Date().toISOString() });
            state.history.push({ role: 'assistant', content: data.reply || '', timestamp: new Date().toISOString() });

            // Render AI Response
            appendAIResponse(data);

        } catch (err) {
            console.error('AI chat error:', err);
            removeThinkingIndicator(thinkingIndicator);
            appendErrorMessage('Unable to connect to GharFix AI. Please check your network and try again.');
        } finally {
            state.isThinking = false;
            setSendingState(false);
            scrollToBottom();
        }
    }

    function setSendingState(sending) {
        if (dom.sendBtn) {
            dom.sendBtn.disabled = sending;
            dom.sendBtn.innerHTML = sending
                ? '<i class="fa-solid fa-spinner fa-spin"></i>'
                : '<i class="fa-solid fa-arrow-up"></i>';
        }
    }

    function scrollToBottom() {
        if (dom.messages) {
            dom.messages.scrollTop = dom.messages.scrollHeight;
        }
    }

    // DOM Message Appenders
    function appendUserMessage(text, imgDataUrl) {
        const timeStr = formatCurrentTime();
        const msgDiv = document.createElement('div');
        msgDiv.className = 'gf-ai-msg gf-ai-msg-user';

        let html = '<div class="gf-ai-bubble gf-ai-bubble-user">';
        if (imgDataUrl) {
            html += `<div class="gf-ai-user-img-wrap"><img src="${imgDataUrl}" alt="User upload" class="gf-ai-user-img" /></div>`;
        }
        if (text) {
            html += `<div class="gf-ai-text">${escapeHtml(text)}</div>`;
        }
        html += `<span class="gf-ai-time">${timeStr}</span></div>`;

        msgDiv.innerHTML = html;
        dom.messages.appendChild(msgDiv);
    }

    function appendThinkingIndicator() {
        const ind = document.createElement('div');
        ind.className = 'gf-ai-msg gf-ai-msg-assistant gf-ai-thinking-row';
        ind.innerHTML = `
            <div class="gf-ai-avatar"><i class="fa-solid fa-wand-magic-sparkles"></i></div>
            <div class="gf-ai-bubble gf-ai-bubble-ai gf-ai-thinking">
                <span class="gf-ai-dot"></span>
                <span class="gf-ai-dot"></span>
                <span class="gf-ai-dot"></span>
            </div>
        `;
        dom.messages.appendChild(ind);
        return ind;
    }

    function removeThinkingIndicator(ind) {
        if (ind && ind.parentNode) {
            ind.parentNode.removeChild(ind);
        }
    }

    function appendErrorMessage(errorText) {
        const msgDiv = document.createElement('div');
        msgDiv.className = 'gf-ai-msg gf-ai-msg-assistant';
        msgDiv.innerHTML = `
            <div class="gf-ai-avatar"><i class="fa-solid fa-triangle-exclamation" style="color:#dc2626;"></i></div>
            <div class="gf-ai-bubble gf-ai-bubble-ai gf-ai-bubble-error">
                <div class="gf-ai-text">${escapeHtml(errorText)}</div>
                <span class="gf-ai-time">${formatCurrentTime()}</span>
            </div>
        `;
        dom.messages.appendChild(msgDiv);
    }

    function appendAIResponse(data) {
        const timeStr = formatCurrentTime();
        const msgDiv = document.createElement('div');
        msgDiv.className = 'gf-ai-msg gf-ai-msg-assistant';

        let cardsHtml = '';

        // 1. Structured Service Category Pill
        if (data.serviceCategory) {
            cardsHtml += `
                <div class="gf-ai-category-pill">
                    <i class="fa-solid fa-wrench"></i> Recommended Service: 
                    <strong>${escapeHtml(data.serviceCategory)}</strong>
                    <a href="/book/${encodeURIComponent(data.serviceCategory)}" class="gf-ai-pill-link">Book Now &rarr;</a>
                </div>
            `;
        }

        // 2. Structured Cost Estimate Card
        if (data.costEstimate) {
            cardsHtml += renderCostEstimateCard(data.costEstimate);
        }

        // 3. Action Proposal Card (Requires Explicit Confirmation)
        if (data.proposedAction) {
            cardsHtml += renderActionProposalCard(data.proposedAction);
        }

        const formattedReply = renderMarkdown(data.reply || '');

        msgDiv.innerHTML = `
            <div class="gf-ai-avatar"><i class="fa-solid fa-wand-magic-sparkles"></i></div>
            <div class="gf-ai-bubble gf-ai-bubble-ai">
                <div class="gf-ai-text">${formattedReply}</div>
                ${cardsHtml}
                <span class="gf-ai-time">${timeStr}</span>
            </div>
        `;

        dom.messages.appendChild(msgDiv);

        // Bind interactive proposal buttons if rendered
        if (data.proposedAction && data.proposedAction.confirmationToken) {
            bindProposalButtons(msgDiv, data.proposedAction);
        }
    }

    // Render Cost Estimate Card
    function renderCostEstimateCard(est) {
        const isGrounded = est.pricingBasis === 'CATALOG_GROUNDED' || est.databaseGrounded;
        const badgeClass = isGrounded ? 'grounded' : 'preliminary';
        const badgeText = isGrounded ? 'GharFix Verified Catalog Rates' : 'Preliminary AI Estimate';
        const badgeIcon = isGrounded ? 'fa-shield-check' : 'fa-circle-info';

        let priceText = '';
        if (est.minPrice != null && est.maxPrice != null) {
            priceText = `&#8377;${est.minPrice} &ndash; &#8377;${est.maxPrice}`;
        } else if (est.standardRate != null) {
            priceText = `&#8377;${est.standardRate}`;
        } else {
            priceText = 'Standard Catalog Rates Apply';
        }

        const serviceName = est.serviceCategory || 'Service';
        const disclaimer = est.disclaimer || 'Final price requires on-site professional inspection.';

        return `
            <div class="gf-ai-card gf-ai-cost-card">
                <div class="gf-ai-card-header">
                    <span class="gf-ai-badge ${badgeClass}"><i class="fa-solid ${badgeIcon}"></i> ${badgeText}</span>
                    <span class="gf-ai-card-service">${escapeHtml(serviceName)}</span>
                </div>
                <div class="gf-ai-card-price-row">
                    <div class="gf-ai-price-label">Estimated Range:</div>
                    <div class="gf-ai-price-val">${priceText}</div>
                </div>
                <div class="gf-ai-card-disclaimer"><i class="fa-solid fa-circle-exclamation"></i> ${escapeHtml(disclaimer)}</div>
                <div class="gf-ai-card-action">
                    <a href="/book/${encodeURIComponent(serviceName)}" class="gf-ai-btn-book">Book ${escapeHtml(serviceName)} Now</a>
                </div>
            </div>
        `;
    }

    // Render Action Proposal Card
    function renderActionProposalCard(action) {
        const token = action.confirmationToken;
        const prompt = action.confirmationPrompt || action.description || 'Please confirm if you wish to proceed with this request.';
        const actionType = (action.actionType || 'ACTION').replace('_', ' ');

        let detailsHtml = '';
        if (action.parameters && Object.keys(action.parameters).length > 0) {
            detailsHtml += '<div class="gf-ai-action-params">';
            if (action.parameters.bookingId) {
                detailsHtml += `<div><span class="gf-param-key">Booking:</span> #${action.parameters.bookingId}</div>`;
            }
            if (action.parameters.serviceCategory) {
                detailsHtml += `<div><span class="gf-param-key">Service:</span> ${escapeHtml(action.parameters.serviceCategory)}</div>`;
            }
            if (action.parameters.refundAmount != null) {
                detailsHtml += `<div><span class="gf-param-key">Refundable:</span> &#8377;${action.parameters.refundAmount}</div>`;
            }
            detailsHtml += '</div>';
        }

        return `
            <div class="gf-ai-card gf-ai-action-proposal-card" id="gfActionCard-${token || 'static'}">
                <div class="gf-ai-action-header">
                    <div class="gf-ai-action-badge"><i class="fa-solid fa-triangle-exclamation"></i> Action Proposal: ${escapeHtml(actionType)}</div>
                </div>
                <div class="gf-ai-action-desc">${escapeHtml(action.description || '')}</div>
                ${detailsHtml}
                <div class="gf-ai-action-prompt">${escapeHtml(prompt)}</div>
                ${token ? `
                    <div class="gf-ai-action-buttons">
                        <button type="button" class="gf-ai-action-btn gf-ai-btn-confirm" data-token="${escapeHtml(token)}">
                            <i class="fa-solid fa-circle-check"></i> Confirm Action
                        </button>
                        <button type="button" class="gf-ai-action-btn gf-ai-btn-dismiss" data-token="${escapeHtml(token)}">
                            <i class="fa-solid fa-xmark"></i> Cancel
                        </button>
                    </div>
                ` : `
                    <div class="gf-ai-action-note">
                        <a href="/login" onclick="openPopup && openPopup('loginBox'); return false;" class="gf-ai-signin-link">Sign in to confirm this action</a>
                    </div>
                `}
            </div>
        `;
    }

    // Bind Confirm & Dismiss Handlers for Human-in-the-Loop Action Execution
    function bindProposalButtons(container, action) {
        const token = action.confirmationToken;
        if (!token) return;

        const card = container.querySelector(`#gfActionCard-${token}`);
        if (!card) return;

        const confirmBtn = card.querySelector('.gf-ai-btn-confirm');
        const dismissBtn = card.querySelector('.gf-ai-btn-dismiss');

        if (confirmBtn) {
            confirmBtn.addEventListener('click', async function() {
                await executeAction(card, token, confirmBtn, dismissBtn);
            });
        }

        if (dismissBtn) {
            dismissBtn.addEventListener('click', async function() {
                await dismissAction(card, token, confirmBtn, dismissBtn);
            });
        }
    }

    async function executeAction(card, token, confirmBtn, dismissBtn) {
        if (state.activeActionTokens.has(token)) return;
        state.activeActionTokens.add(token);

        confirmBtn.disabled = true;
        dismissBtn.disabled = true;
        const originalHtml = confirmBtn.innerHTML;
        confirmBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Processing...';

        try {
            const res = await fetch('/api/ai/actions/confirm', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                credentials: 'same-origin',
                body: JSON.stringify({ confirmationToken: token })
            });

            const data = await res.json();

            if (res.ok && data.success) {
                // Render Execution Success Badge
                const refId = data.referenceId ? `Ref: #${data.referenceId}` : '';
                card.innerHTML = `
                    <div class="gf-ai-result-box success">
                        <div class="gf-ai-result-title"><i class="fa-solid fa-circle-check"></i> Action Executed Successfully</div>
                        <div class="gf-ai-result-msg">${escapeHtml(data.message || 'The action has been completed.')}</div>
                        ${refId ? `<div class="gf-ai-result-ref">${refId}</div>` : ''}
                    </div>
                `;
                showToast('Action confirmed and completed successfully!', 'success');
            } else {
                const errMsg = data.message || data.error || 'Execution failed';
                card.innerHTML = `
                    <div class="gf-ai-result-box error">
                        <div class="gf-ai-result-title"><i class="fa-solid fa-circle-xmark"></i> Action Could Not Be Executed</div>
                        <div class="gf-ai-result-msg">${escapeHtml(errMsg)}</div>
                    </div>
                `;
                showToast(errMsg, 'error');
            }
        } catch (e) {
            confirmBtn.disabled = false;
            dismissBtn.disabled = false;
            confirmBtn.innerHTML = originalHtml;
            state.activeActionTokens.delete(token);
            showToast('Failed to connect to server. Please try again.', 'error');
        }
    }

    async function dismissAction(card, token, confirmBtn, dismissBtn) {
        confirmBtn.disabled = true;
        dismissBtn.disabled = true;

        try {
            await fetch('/api/ai/actions/dismiss', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ confirmationToken: token })
            });
        } catch (ignored) {}

        card.innerHTML = `
            <div class="gf-ai-result-box dismissed">
                <div class="gf-ai-result-msg"><i class="fa-solid fa-ban"></i> Action proposal was cancelled. No changes were made.</div>
            </div>
        `;
    }

    // Markdown & Text Formatter Helper
    function renderMarkdown(text) {
        if (!text) return '';
        let escaped = escapeHtml(text);

        // Bold **text**
        escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
        // Italic *text*
        escaped = escaped.replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>');

        // Bullet lists
        const lines = escaped.split('\n');
        let inList = false;
        let result = [];

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();
            if (line.startsWith('* ') || line.startsWith('- ')) {
                if (!inList) {
                    result.push('<ul class="gf-ai-list">');
                    inList = true;
                }
                result.push(`<li>${line.substring(2)}</li>`);
            } else if (/^\d+\.\s/.test(line)) {
                if (!inList) {
                    result.push('<ol class="gf-ai-list">');
                    inList = true;
                }
                result.push(`<li>${line.replace(/^\d+\.\s/, '')}</li>`);
            } else {
                if (inList) {
                    result.push('</ul>');
                    inList = false;
                }
                if (line) {
                    result.push(`<p>${line}</p>`);
                }
            }
        }
        if (inList) result.push('</ul>');

        return result.join('');
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatCurrentTime() {
        const now = new Date();
        let hours = now.getHours();
        let minutes = now.getMinutes();
        const ampm = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12 || 12;
        minutes = minutes < 10 ? '0' + minutes : minutes;
        return `${hours}:${minutes} ${ampm}`;
    }

    // Toast notification utility
    function showToast(message, type = 'info') {
        if (typeof showPaymentToast === 'function') {
            showPaymentToast(message, type);
            return;
        }
        let container = document.getElementById('gf-toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'gf-toast-container';
            container.style.cssText = 'position:fixed;top:80px;right:20px;z-index:99999;display:flex;flex-direction:column;gap:8px;pointer-events:none;';
            document.body.appendChild(container);
        }
        const toast = document.createElement('div');
        toast.style.cssText = `
            padding: 12px 18px; border-radius: 8px; font-size: 13px; font-weight: 600;
            color: #fff; box-shadow: 0 4px 16px rgba(0,0,0,0.15); pointer-events: auto;
            background: ${type === 'success' ? '#14532D' : type === 'error' ? '#991B1B' : '#1A1A1A'};
            animation: flashSlideIn 0.3s ease;
        `;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transition = 'opacity 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3500);
    }

    // DOM Ready Bootstrap
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
