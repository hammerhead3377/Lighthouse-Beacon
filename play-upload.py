#!/usr/bin/env python3
"""Upload a signed AAB to Google Play Internal Testing track."""

import sys
import os
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.aistudio.patricia.capture.vts"
KEY_FILE = os.path.expanduser("~/.config/play-store/lighthouse-beacon-sa.json")
AAB_PATH = "app/build/outputs/bundle/release/app-release.aab"
TRACK = "internal"

def upload(release_notes="Bug fixes and stability improvements."):
    creds = service_account.Credentials.from_service_account_file(
        KEY_FILE,
        scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    service = build("androidpublisher", "v3", credentials=creds)
    edits = service.edits()

    edit = edits.insert(packageName=PACKAGE, body={}).execute()
    edit_id = edit["id"]
    print(f"Edit opened: {edit_id}")

    media = MediaFileUpload(AAB_PATH, mimetype="application/octet-stream", resumable=True)
    bundle = edits.bundles().upload(
        packageName=PACKAGE,
        editId=edit_id,
        media_body=media
    ).execute()
    version_code = bundle["versionCode"]
    print(f"AAB uploaded — versionCode {version_code}")

    edits.tracks().update(
        packageName=PACKAGE,
        editId=edit_id,
        track=TRACK,
        body={
            "releases": [{
                "versionCodes": [str(version_code)],
                "status": "completed",
                "releaseNotes": [{"language": "en-US", "text": release_notes}]
            }]
        }
    ).execute()
    print(f"Track '{TRACK}' updated")

    edits.commit(packageName=PACKAGE, editId=edit_id).execute()
    print("Edit committed — release live in Play Console")

if __name__ == "__main__":
    notes = sys.argv[1] if len(sys.argv) > 1 else "Bug fixes and stability improvements."
    upload(notes)
