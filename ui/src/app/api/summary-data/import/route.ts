import { NextRequest } from "next/server";
import { auth } from "@/auth";
import { unauthorizedResponse, errorResponse } from "../../_lib/backend-fetch";

const BACKEND_URL = process.env.BACKEND_URL!;

export async function POST(request: NextRequest) {
  const session = await auth();

  if (!session?.accessToken) {
    return unauthorizedResponse();
  }

  try {
    // Get the form data from the request
    const formData = await request.formData();
    
    // Forward the form data to the backend
    const backendPath = `/summary-data/import`;
    const url = `${BACKEND_URL}${backendPath}`;

    const response = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
      },
      body: formData, // Send the FormData directly
    });

    if (!response.ok) {
      return errorResponse(`Backend error: ${response.status}`, response.status);
    }

    const data = await response.json();
    return Response.json(data);
  } catch (error) {
    console.error("Error uploading file:", error);
    return errorResponse("Failed to upload file", 500);
  }
}

