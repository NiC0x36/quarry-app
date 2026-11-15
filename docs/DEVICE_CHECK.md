# First-device check

Use a checked **Quarry Local** APK. It has a separate package identity and does not replace upstream Quarry.

1. Install the APK. At first launch, decline storage access and confirm that the app explains the missing permission instead of claiming a complete scan.
2. Grant access through Android's storage settings and run **Analyze Storage**. Keep the app visible for this first check.
3. Open **Explore → Folders**. Open and return from a few known folders; verify names, paths and approximate sizes against the device file manager. All shown sizes refer to indexed, accessible files.
4. Open **Treemap**. Larger byte counts should occupy larger areas. Use pinch zoom or **List** for tiny entries. Unreadable and excluded folders have no fabricated area; their status remains available in lists.
5. Check the overview: indexed files, unindexed used space and free space should form the capacity bar. The unindexed remainder must not be presented as removable junk.
6. Confirm that file details have no delete, trash, rename, external-open or share controls. APK files must not launch an installer.
7. Cancel a scan, then run another complete scan. Existing user files must remain unchanged.
8. Change an exclusion or hidden-file scan setting. The app must request a new scan; run it before comparing results.
9. Open several folders and switch repeatedly between **Folders**, **List** and **Treemap**, including a folder with many entries. Check whether navigation remains responsive and whether any unexpected closure occurs. A resource-pressure fix is not proof that every reported crash is resolved.

Report only whether these checks pass and any error text needed for diagnosis. Use synthetic folders for screenshots or public bug reports; do not publish personal paths or scan contents.

APK permission and backup-resource checks are performed before installation. A real backup/transfer runtime test has not been performed. Static verification and Android's no-backup storage reduce exposure, but are not certification of every vendor's backup transport.
