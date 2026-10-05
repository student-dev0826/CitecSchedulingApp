CITEC Scheduling - update notes
================================
1) SERVER (do this first)
   Upload to your hosting's api/ folder (same place as login.php):
     backend/api/schedules.php   (NEW - all scheduling + notifications)
     backend/api/login.php       (updated - fixes a repeated-parameter query)
   schedules.php creates its own tables (schedules, notifications) on first use.
   Open  https://citecscheduling.x10.mx/api/schedules.php  in a browser:
   you should see {"success":true,"message":"Schedules API is active and online."}

2) APP
   Copy the files under app/ over your project (same paths), then Build > Rebuild Project.
   If you ship updates through version.json, raise versionCode in app/build.gradle.kts.
   Everyone must log in again once (old offline sessions have no server account id).
