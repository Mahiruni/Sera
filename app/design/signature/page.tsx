import {notFound} from "next/navigation";
import SignaturePreview from "./preview";
export const metadata={title:"Signature design preview | Sera",robots:{index:false,follow:false}};
export const dynamic="force-dynamic";
export default function SignatureDesignPage(){
 if(process.env.NODE_ENV!=="development"&&process.env.VERCEL_ENV!=="preview")notFound();
 return <main style={{maxWidth:1100,margin:"0 auto",padding:"100px 16px 96px"}}><SignaturePreview/></main>;
}
