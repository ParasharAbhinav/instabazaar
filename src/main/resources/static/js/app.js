'use strict';

if (document.querySelector('[data-video-pending="true"]')) {
  setTimeout(() => location.reload(), 3000);
}

document.querySelectorAll('[data-video-input]').forEach(input => {
  input.addEventListener('change', () => {
    const file = input.files[0];
    const feedback = input.parentElement.querySelector('.file-feedback');
    const message = file && file.size > 30 * 1024 * 1024 ? 'Choose an MP4 no larger than 30 MB.' : '';
    input.setCustomValidity(message);
    if (feedback) feedback.textContent = message || (file ? 'Selected: ' + file.name : '');
  });
});

document.querySelectorAll('[data-image-input]').forEach(input => {
  input.addEventListener('change', () => {
    const file = input.files[0];
    const feedback = input.parentElement.querySelector('.file-feedback');
    let message = '';
    if (file && file.size > 5 * 1024 * 1024) message = 'Please choose an image no larger than 5 MB.';
    else if (file && !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) message = 'Choose a JPEG, PNG or WebP image.';
    input.setCustomValidity(message);
    if (feedback) feedback.textContent = message || (file ? 'Selected: ' + file.name : '');
  });
});
