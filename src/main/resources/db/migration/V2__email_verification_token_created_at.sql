alter table email_verification_tokens add column created_at timestamp(6);

update email_verification_tokens
set created_at = expires_at - interval '24 hours';

alter table email_verification_tokens alter column created_at set not null;
