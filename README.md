# دستیار مدل زبانی (SLM Chat) — اجرای مدل‌های زبانی کوچک روی اندروید

یک اپ اندرویدی ساده که مدل‌های زبانی کوچک (SLM) را **کاملاً آفلاین و روی خود گوشی** اجرا می‌کند.
موتور اجرا: [MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android) گوگل (کتابخانه‌ی `com.google.mediapipe:tasks-genai`).

## امکانات

- دانلود مستقیم فایل مدل (`.task`) از یک لینک، با نوار پیشرفت
- یا انتخاب دستی فایل مدل از حافظه‌ی گوشی
- مدیریت چند مدل ذخیره‌شده (بارگذاری / حذف)
- چت با پاسخ جریانی (استریم توکن‌به‌توکن)
- ساخت خودکار APK با GitHub Actions روی هر push یا هر تگ نسخه

## ساختار پروژه

```
app/src/main/java/ir/example/slmchat/
├── MainActivity.kt          # فعالیت اصلی و ناوبری بین دو صفحه
├── llm/LlmModelManager.kt   # لایه‌ی نازک روی MediaPipe LLM Inference API
├── data/ModelRepository.kt  # دانلود/وارد کردن/مدیریت فایل‌های مدل
└── ui/
    ├── ChatViewModel.kt     # وضعیت برنامه (StateFlow)
    ├── ModelScreen.kt       # صفحه‌ی انتخاب و دانلود مدل
    ├── ChatScreen.kt        # صفحه‌ی چت
    └── theme/Theme.kt
```

## اجرای محلی (Android Studio)

1. پروژه را در Android Studio (Koala یا جدیدتر) باز کنید.
2. صبر کنید Gradle sync تمام شود.
3. روی دستگاه واقعی (نه امولاتور) اجرا کنید — API فقط روی گوشی‌های نسبتاً قدرتمند مثل
   Pixel 8 یا Samsung S23 به‌بالا به‌طور قابل‌اعتماد کار می‌کند.

## ساخت خودکار APK روی گیت‌هاب

فایل `.github/workflows/build-apk.yml` از قبل آماده است:

- با هر **push به شاخه‌ی `main`**، یک APK دیباگ ساخته و به‌عنوان *Artifact* در تب Actions
  آپلود می‌شود.
- اگر یک **تگ به شکل `v1.0.0`** بزنید و push کنید (`git tag v1.0.0 && git push --tags`)،
  همان APK به‌صورت خودکار به یک GitHub Release پیوست می‌شود.
- برای ساخت دستی، از تب Actions گزینه‌ی «Run workflow» را بزنید.

نیازی به commit کردن فایل `gradle-wrapper.jar` نیست؛ ورک‌فلو خودش Gradle 8.9 را نصب می‌کند.

> برای انتشار عمومی، بهتر است به‌جای APK دیباگ، یک نسخه‌ی release امضاشده بسازید
> (نیاز به keystore و اضافه‌کردن مراحل امضا در همین workflow دارد).

## از کجا مدل رایگان بگیرم؟

مدل باید در فرمت `.task` (یا `.litertlm`) باشد. چند گزینه‌ی رایگان:

| مدل | حجم تقریبی | لینک |
|---|---|---|
| Gemma-3 1B IT (کوانتایز 4-bit) | ~500MB | huggingface.co/litert-community/Gemma3-1B-IT |
| Gemma-2 2B | ~1.3GB | جستجوی «Gemma-2 2B LiteRT» در Hugging Face / Kaggle |
| Phi-2 | ~1.5GB | جستجوی «Phi-2 LiteRT MediaPipe» |
| Falcon-1B / StableLM | چند صد مگابایت | مستندات LLM Inference گوگل، بخش Models |

نکات مهم:

- مدل‌های خانواده‌ی **Gemma** روی Hugging Face «گیت‌شده» هستند: باید رایگان وارد حساب
  Hugging Face شوید، شرایط استفاده را بپذیرید و یک **Access Token** بسازید. سپس هنگام
  دانلود در اپ، لینک مستقیم فایل را بدهید (در صورت نیاز به هدر `Authorization: Bearer <token>`
  می‌توانید تابع `downloadModel` در `ModelRepository.kt` را با یک هدر سفارشی صدا بزنید).
- مدل‌هایی مثل Phi-2 و Falcon معمولاً بدون گیت و کاملاً باز هستند.
- فایل‌های مدل حجیم‌اند (چند صد مگابایت تا چند گیگابایت) و **هرگز نباید داخل ریپوی گیت‌هاب
  کامیت شوند** — به همین دلیل در `.gitignore` کنار گذاشته شده‌اند. کاربر باید آن‌ها را
  داخل خود اپ دانلود کند یا از حافظه‌ی گوشی انتخاب کند.

## محدودیت‌های فعلی API

- MediaPipe LLM Inference در حالت **maintenance-only** است؛ گوگل مهاجرت به
  [LiteRT-LM Android API](https://developers.google.com/edge/litert-lm/android) را برای آینده پیشنهاد می‌دهد.
- روی امولاتور به‌طور قابل‌اعتماد اجرا نمی‌شود.
- این پیاده‌سازی متنی-به-متنی ساده است؛ ورودی تصویر/صدا (multimodal) در کد فعلی فعال نیست،
  ولی MediaPipe از آن پشتیبانی می‌کند (به بخش «Multimodal prompting» در مستندات رسمی مراجعه کنید).
