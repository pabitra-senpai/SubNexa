# আপলোড গাইড (বাংলা)

## ধাপ ১: GitHub repository বানান
1. GitHub-এ গিয়ে **New repository** চাপুন। নাম দিন `SubNexa`।
2. **Public** রাখুন (Private হলে অন্যরা Download বাটন দিয়ে APK নামাতে পারবে না)।
3. **Create repository** চাপুন।

## ধাপ ২: এই ZIP-এর ফাইল আপলোড
1. ZIP unzip করুন।
2. Repository পেজে **Add file → Upload files** চাপুন।
3. `README.md`, `.gitignore`, `.github` ফোল্ডার, `docs` ফোল্ডার আর `design-assets` ফোল্ডার ড্র্যাগ করে দিন, তারপর **Commit changes**।
4. `.github` ফোল্ডার না গেলে (Mac-এ hidden থাকে): **Add file → Create new file** চাপুন, নামের ঘরে `.github/workflows/build-apk.yml` লিখুন, ফাইলের লেখা পেস্ট করে commit করুন।
5. `README.md` খুলে সব `YOUR_USERNAME` মুছে আপনার GitHub username বসান (edit আইকন দিয়ে)।

## ধাপ ৩: Actions permission ঠিক করুন (একবারই)
Repository → **Settings → Actions → General → Workflow permissions** → **Read and write permissions** সিলেক্ট করে **Save** চাপুন। এটা না করলে Release তৈরি হবে না, Download বাটন কাজ করবে না।

## ধাপ ৪: Google AI Studio-তে অ্যাপ বানান
1. aistudio.google.com খুলে নতুন চ্যাট করুন, সবচেয়ে শক্তিশালী মডেল বেছে নিন।
2. `docs/AI_STUDIO_PROMPT.md` ফাইলের **"---" লাইনের নিচের সবটুকু** কপি করে এক মেসেজে পাঠান।
3. উত্তর মাঝপথে থামলে লিখুন `continue`।
4. সব ফাইল আসার পর `docs/FOLLOW_UP_PROMPTS.md`-এর **২ নম্বর প্রম্পট** দিন। এটা ভুল ধরে ঠিক করে দেবে।

## ধাপ ৫: AI Studio-র ফাইল repository-তে তুলুন
- সহজ উপায়: repository পেজে কিবোর্ডে `.` (ডট) চাপুন। ব্রাউজারেই VS Code খুলবে। সেখানে ফাইল বানিয়ে পাথ অনুযায়ী পেস্ট করুন, তারপর Source Control থেকে commit করুন।
- বিকল্প: **Add file → Create new file**, নামের ঘরে পুরো পাথ লিখুন (যেমন `app/src/main/java/com/subnexa/editor/MainActivity.kt`), কোড পেস্ট করে commit করুন।
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` repository-র একদম root-এ যাবে।

## ধাপ ৬: APK নামান
1. `main` branch-এ commit করলেই **Actions** ট্যাবে "Build APK" চলবে (৫ থেকে ১০ মিনিট)।
2. সবুজ টিক এলে README-র **Download APK** বাটনে চাপ দিন, অথবা **Releases → SubNexa latest build** থেকে `SubNexa.apk` নামান।
3. ফোনে APK খুলে Install করুন। Android চাইলে "Unknown sources" অনুমতি দিন।

## বিল্ড ফেইল করলে
Actions → লাল রানটি খুলে error লাইনগুলো কপি করুন, তারপর `docs/FOLLOW_UP_PROMPTS.md`-এর **৩ নম্বর প্রম্পট** দিয়ে AI Studio-তে পাঠান। ঠিক করা ফাইল আবার commit করুন।

## নম্বরযুক্ত রিলিজ (ঐচ্ছিক)
Repository → **Releases → Create a new release** → tag লিখুন `v1.0.0` → Publish। APK নিজে থেকে যুক্ত হবে।
