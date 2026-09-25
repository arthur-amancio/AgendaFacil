document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.alert.ok, .alert.success').forEach(el => {
    setTimeout(() => { el.style.transition='opacity .35s ease'; el.style.opacity='0'; }, 4200);
  });

  document.querySelectorAll('[data-modal-open]').forEach(button => {
    button.addEventListener('click', () => {
      const modal = document.getElementById(button.dataset.modalOpen);
      if (modal && typeof modal.showModal === 'function') {
        modal.showModal();
      }
    });
  });

  document.querySelectorAll('[data-modal-close]').forEach(button => {
    button.addEventListener('click', () => {
      button.closest('dialog')?.close();
    });
  });

  document.querySelectorAll('dialog.modal').forEach(dialog => {
    dialog.addEventListener('click', event => {
      if (event.target === dialog) {
        dialog.close();
      }
    });
  });

  document.querySelectorAll('[data-business-hours-row]').forEach(row => {
    const open = row.querySelector('[data-business-hours-open]');
    const times = row.querySelectorAll('[data-business-hours-time]');
    const sync = () => times.forEach(input => { input.required = open.checked; });
    open?.addEventListener('change', sync);
    sync();
  });
});
