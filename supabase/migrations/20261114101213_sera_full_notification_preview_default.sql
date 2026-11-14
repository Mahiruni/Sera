-- Default applies to new preference rows; preserve all existing user choices.
alter table public.sera_message_preferences alter column notification_preview_mode set default 'full', alter column notification_previews set default true;
