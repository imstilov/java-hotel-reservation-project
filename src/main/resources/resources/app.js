// ============================================================
// Универсальный helper для запросов к API
// ============================================================
async function apiRequest(url, method = 'GET', body = null) {
    const options = {
        method: method,
        headers: { 'Content-Type': 'application/json' }
    };
    if (body) {
        options.body = JSON.stringify(body);
    }

    const response = await fetch(url, options);

    if (response.status === 204) return null;

    const json = await response.json();

    // Бэкенд возвращает { success, message, data, requestTime }
    if (!response.ok || json.success === false) {
        throw new Error(json.message || `HTTP ${response.status}`);
    }

    return json.data;
}

// ============================================================
// Helper для отображения результата
// ============================================================
function showResult(elementId, text, isError = false) {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.textContent = text;
    el.className = 'result ' + (isError ? 'error' : 'success');
}

// ============================================================
// РЕГИСТРАЦИЯ — работает только на register.html
// ============================================================
const userForm = document.getElementById('userForm');
if (userForm) {
    userForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const formData = new FormData(e.target);
        const userData = Object.fromEntries(formData);

        try {
            const created = await apiRequest('/hotel/users/create', 'POST', userData);
            showResult('userResult',
                `✓ Создан пользователь: ID=${created.id}, ${created.email}`);
            e.target.reset();
        } catch (error) {
            showResult('userResult', '✗ Ошибка: ' + error.message, true);
        }
    });
}

// ============================================================
// СПИСОК ЮЗЕРОВ — работает только на register.html
// ============================================================
const loadUsersBtn = document.getElementById('loadUsersBtn');
if (loadUsersBtn) {
    loadUsersBtn.addEventListener('click', async () => {
        try {
            const users = await apiRequest('/hotel/users');
            const list = document.getElementById('usersList');
            list.innerHTML = '';

            if (!users || users.length === 0) {
                list.innerHTML = '<li>Пока нет зарегистрированных пользователей</li>';
                return;
            }

            users.forEach(user => {
                const li = document.createElement('li');
                li.textContent = `#${user.id} — ${user.firstName} ${user.lastName ?? ''} (${user.email})`;
                list.appendChild(li);
            });
        } catch (error) {
            document.getElementById('usersList').innerHTML =
                `<li style="color: var(--error-text)">Ошибка: ${error.message}</li>`;
        }
    });
}

// ============================================================
// БРОНИРОВАНИЕ — работает только на reservation.html
// ============================================================
const reservationForm = document.getElementById('reservationForm');
if (reservationForm) {
    reservationForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const formData = new FormData(e.target);
        const reservationData = {
            userId:    Number(formData.get('userId')),
            roomId:    Number(formData.get('roomId')),
            startDate: formData.get('startDate'),
            endDate:   formData.get('endDate')
        };

        try {
            const created = await apiRequest('/reservation', 'POST', reservationData);
            showResult('reservationResult',
                `✓ Бронь #${created.id} создана. Статус: ${created.status}`);
            e.target.reset();
        } catch (error) {
            showResult('reservationResult', '✗ Ошибка: ' + error.message, true);
        }
    });
}