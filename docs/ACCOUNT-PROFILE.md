# Professional account profile

`/settings` shows a private profile with a separate Save/Cancel editor. Profile details persist through the existing owner-only `profiles` policies. New headline, summary and education fields are constrained by both server validation and SQL checks.

Photos can be cropped, zoomed and repositioned in the browser. Cropped files are decoded again on the server, rotated, resized to 512px and encoded as JPEG. Image input is limited to JPG/PNG/WebP, 8 MB before client cropping, 2 MB on the server and 16 megapixels. Originals of profile photos are not stored.

CV uploads accept PDF up to 3 MB. The exact original bytes are stored in a private bucket. A separate PDF is created by embedding each original page into a new page with a dedicated 38-point footer strip. This avoids overlaying CV content. The branded copy is a static visual derivative: interactive fields, annotations and digital signatures are not carried over. Password-protected, malformed, unusual-sized or over-40-page PDFs can be kept as originals but branding may fail; the account shows that state and offers retry or replacement.

The bucket is private, owner-scoped and restricted by MIME type and size. The authenticated file endpoint derives the path from the current user's metadata; it never accepts a user ID or arbitrary object path. Download links expire after 60 seconds. Preview responses are authenticated, uncached and sandboxed. CVs and photos are never included in applications or exposed to employers by this release. No malware-scan or verified-employer claim is made.

File replacement uses unique object names and a metadata version check. Concurrent stale updates are rejected and new unused objects cleaned up. Successful replacements and removals clean up prior objects through the Storage API; a provider cleanup failure can leave an inaccessible-to-others orphan owned by the same user. Deleting the Auth account does not automatically purge storage objects; existing manual account deletion procedures must clean storage first.

Validation includes 19 unit tests, a production build, clean lint, a rolled-back hosted RLS test covering owner/outsider/anonymous access, PDF dimension/source-preservation checks and rendered portrait/rotated PDF inspection. Production HTTP and browser checks should be recorded with the release result. The existing Auth leaked-password-protection warning remains outside this change.
