/**
 * GharFix Razorpay Standard Web Checkout Integration
 */
let cachedRazorpayKey = null;

async function getRazorpayKey() {
    if (cachedRazorpayKey) return cachedRazorpayKey;
    try {
        const res = await fetch('/api/razorpay/key');
        if (!res.ok) throw new Error('Failed to fetch Razorpay key');
        const data = await res.json();
        cachedRazorpayKey = data.key_id;
        return cachedRazorpayKey;
    } catch (err) {
        console.error('Error fetching Razorpay key:', err);
        throw err;
    }
}

function showPaymentToast(message, type = 'info') {
    let container = document.getElementById('payment-toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'payment-toast-container';
        container.style.cssText = `
            position: fixed;
            top: 80px;
            right: 20px;
            z-index: 99999;
            display: flex;
            flex-direction: column;
            gap: 10px;
            pointer-events: none;
        `;
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.style.cssText = `
        padding: 14px 20px;
        border-radius: 10px;
        font-size: 13px;
        font-weight: 600;
        font-family: 'Inter', sans-serif;
        color: #fff;
        box-shadow: 0 10px 30px rgba(0,0,0,0.18);
        display: flex;
        align-items: center;
        gap: 10px;
        pointer-events: auto;
        opacity: 0;
        transform: translateX(40px);
        transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
        max-width: 380px;
        background: ${
            type === 'success' ? '#059669' :
            type === 'error' ? '#dc2626' :
            type === 'warning' ? '#d97706' : '#1f2937'
        };
    `;

    const icon = type === 'success' ? 'fa-circle-check' :
                 type === 'error' ? 'fa-circle-exclamation' :
                 type === 'warning' ? 'fa-triangle-exclamation' : 'fa-circle-info';

    toast.innerHTML = `<i class="fa-solid ${icon}"></i><span>${message}</span>`;
    container.appendChild(toast);

    // Animate in
    requestAnimationFrame(() => {
        toast.style.opacity = '1';
        toast.style.transform = 'translateX(0)';
    });

    // Remove after 5 seconds
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(40px)';
        setTimeout(() => toast.remove(), 300);
    }, 5000);
}

/**
 * Initiates Razorpay Standard Checkout
 *
 * @param {Object} config
 * @param {number} config.amount Amount in rupees (e.g. 499) OR amountInPaise
 * @param {number} [config.amountInPaise] Amount in paise (minimum 100)
 * @param {number} [config.bookingId] Optional booking id to link
 * @param {string} [config.description] Payment description
 * @param {string} [config.customerName] Customer name
 * @param {string} [config.customerEmail] Customer email
 * @param {string} [config.customerPhone] Customer contact number
 * @param {Function} [config.onSuccess] Callback on successful verification
 * @param {Function} [config.onError] Callback on error
 * @param {Function} [config.onDismiss] Callback on modal dismiss
 */
async function initiateRazorpayPayment(config) {
    const btn = config.triggerButton;
    let originalBtnHtml = '';
    if (btn) {
        originalBtnHtml = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Processing...';
    }

    try {
        if (typeof Razorpay === 'undefined') {
            throw new Error('Razorpay SDK failed to load. Please check your internet connection.');
        }

        const keyId = await getRazorpayKey();
        if (!keyId) {
            throw new Error('Razorpay Key ID is not configured on the server.');
        }

        // Calculate amount in paise
        let amountInPaise = config.amountInPaise;
        if (!amountInPaise && config.amount) {
            amountInPaise = Math.round(Number(config.amount) * 100);
        }
        if (!amountInPaise || amountInPaise < 100) {
            throw new Error('Amount must be at least ₹1.00 (100 paise).');
        }

        // STEP 1: Call backend create-order endpoint
        showPaymentToast('Creating payment order...', 'info');
        const orderRes = await fetch('/api/create-order', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                amount: amountInPaise,
                currency: 'INR',
                receipt: 'rcpt_' + Date.now(),
                booking_id: config.bookingId || null
            })
        });

        const orderData = await orderRes.json();
        if (!orderRes.ok || !orderData.order_id) {
            throw new Error(orderData.message || 'Failed to create Razorpay order');
        }

        // STEP 2: Configure & Open Razorpay Checkout Modal
        const options = {
            key: keyId,
            amount: orderData.amount,
            currency: orderData.currency || 'INR',
            name: 'GharFix',
            description: config.description || 'Home Services Payment',
            image: 'https://cdn-icons-png.flaticon.com/512/1048/1048953.png',
            order_id: orderData.order_id,
            prefill: {
                name: config.customerName || '',
                email: config.customerEmail || '',
                contact: config.customerPhone || ''
            },
            notes: {
                booking_id: config.bookingId ? String(config.bookingId) : 'none'
            },
            theme: {
                color: '#0f0f0f'
            },
            // Handlers
            handler: async function (response) {
                // STEP 3: Receive payment credentials & call verify-payment endpoint
                showPaymentToast('Verifying payment signature with GharFix server...', 'info');
                try {
                    const verifyRes = await fetch('/api/verify-payment', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            razorpay_order_id: response.razorpay_order_id,
                            razorpay_payment_id: response.razorpay_payment_id,
                            razorpay_signature: response.razorpay_signature,
                            booking_id: config.bookingId || null
                        })
                    });

                    const verifyData = await verifyRes.json();
                    if (verifyRes.ok && verifyData.success) {
                        showPaymentToast('Payment Verified Successfully! Ref: ' + response.razorpay_payment_id, 'success');
                        if (config.onSuccess) {
                            config.onSuccess(verifyData, response);
                        } else {
                            setTimeout(() => window.location.reload(), 1500);
                        }
                    } else {
                        showPaymentToast('Verification Failed: ' + (verifyData.message || 'Signature mismatch'), 'error');
                        if (config.onError) config.onError(verifyData);
                    }
                } catch (err) {
                    showPaymentToast('Error verifying payment: ' + err.message, 'error');
                    if (config.onError) config.onError(err);
                } finally {
                    if (btn) {
                        btn.disabled = false;
                        btn.innerHTML = originalBtnHtml;
                    }
                }
            },
            modal: {
                ondismiss: function () {
                    showPaymentToast('Payment cancelled by user.', 'warning');
                    if (btn) {
                        btn.disabled = false;
                        btn.innerHTML = originalBtnHtml;
                    }
                    if (config.onDismiss) config.onDismiss();
                }
            }
        };

        const rzp = new Razorpay(options);

        rzp.on('payment.failed', function (response) {
            const errorDesc = response.error ? (response.error.description || response.error.reason) : 'Payment failed';
            showPaymentToast('Payment Failed: ' + errorDesc, 'error');
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = originalBtnHtml;
            }
            if (config.onError) config.onError(response.error);
        });

        rzp.open();

    } catch (err) {
        showPaymentToast(err.message, 'error');
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = originalBtnHtml;
        }
        if (config.onError) config.onError(err);
    }
}
