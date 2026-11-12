export function canReviewIdentity(user:{email_confirmed_at?:string|null;app_metadata?:Record<string,unknown>}|null|undefined){
 return Boolean(user?.email_confirmed_at&&(["admin","moderator"].includes(String(user.app_metadata?.role))||user.app_metadata?.identity_reviewer===true));
}
