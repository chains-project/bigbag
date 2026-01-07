import sys
import google.generativeai as genai
import os

genai.configure(api_key=os.getenv("GEMINI_API_KEY"))

model = genai.GenerativeModel("gemini-1.5-flash")
prompt = " ".join(sys.argv[1:])

response = model.generate_content(prompt)
print(response.text)
