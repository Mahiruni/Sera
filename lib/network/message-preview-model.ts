export function cleanPreviewText(value:unknown,limit=140):string{
 if(typeof value!=="string")return "";
 return Array.from(value.replace(/[\u0000-\u001f\u007f\u202a-\u202e\u2066-\u2069]/g," ").replace(/\s+/g," ").trim()).slice(0,limit).join("");
}
export function attachmentPreview(mime:unknown):string{
 if(typeof mime!=="string")return "Shared an attachment";
 if(mime.startsWith("image/"))return "Sent you a photo";
 if(mime.startsWith("audio/"))return "Sent a voice message";
 if(mime.startsWith("video/"))return "Sent you a video";
 return mime==="application/pdf"?"Shared a PDF document":"Shared an attachment";
}

export type NotificationPreviewMode="full"|"sender"|"hidden";
export function notificationPreviewMode(mode:unknown,legacy?:boolean):NotificationPreviewMode{
 if(mode==="full"||mode==="sender"||mode==="hidden")return mode;
 return legacy===true?"full":legacy===false?"hidden":"sender";
}

const notificationWords:Record<string,[string,string]> = {
 "Sent you a message":["መልዕክት ልኮልዎታል","Ergaa siif erge"],
 "You have a new private message.":["አዲስ የግል መልዕክት አለዎት።","Ergaa dhuunfaa haaraa qabda."],
 "Sent you a photo":["ፎቶ ልኮልዎታል","Suuraa siif erge"],
 "Sent a voice message":["የድምፅ መልዕክት ልኳል","Ergaa sagalee erge"],
 "Sent you a video":["ቪዲዮ ልኮልዎታል","Viidiyoo siif erge"],
 "Shared a PDF document":["የPDF ሰነድ አጋርቷል","Galmee PDF qoodde"],
 "Shared an attachment":["አባሪ አጋርቷል","Faayila qoodde"],
 "Sent you multiple attachments":["ብዙ አባሪዎችን ልኳል","Faayiloota hedduu siif erge"],
 "Notifications & privacy":["ማሳወቂያዎች እና ግላዊነት","Beeksisootaa fi iccitii"],
 "Show sender and message":["ላኪውን እና መልዕክቱን አሳይ","Ergaa fi nama erge agarsiisi"],
 "Show sender only":["ላኪውን ብቻ አሳይ","Nama erge qofa agarsiisi"],
 "Hide sensitive content":["የግል ይዘትን ደብቅ","Qabiyyee dhuunfaa dhoksi"],
 "Enable browser notifications":["የአሳሽ ማሳወቂያዎችን አንቃ","Beeksisoota biraawzaraa bani"],
 "Turn off browser notifications":["የአሳሽ ማሳወቂያዎችን አጥፋ","Beeksisoota biraawzaraa cufi"],
 "Message previews":["የመልዕክት ቅድመ እይታ","Mul’ata ergaa"],
 "Save preview preference":["የቅድመ እይታ ምርጫን አስቀምጥ","Filannoo mul’ataa olkaa’i"],
 "Sera member":["የSera አባል","Miseensa Sera"]
};
export function notificationText(locale:string,value:string):string{
 const words=notificationWords[value];const language=locale.toLowerCase().split(/[-,;]/)[0];
 return words?(language==="am"?words[0]:language==="om"?words[1]:value):value;
}
