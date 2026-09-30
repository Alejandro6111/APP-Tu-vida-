import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join } from "node:path";

const port = Number(process.env.PORT) || 4173;
const mime = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
};

createServer(async (request, response) => {
  const route = request.url === "/" ? "/index.html" : request.url.split("?")[0];
  try {
    const file = await readFile(join(process.cwd(), route));
    response.writeHead(200, { "Content-Type": mime[extname(route)] || "application/octet-stream" });
    response.end(file);
  } catch {
    response.writeHead(404);
    response.end("No encontrado");
  }
}).listen(port, () => {
  console.log(`Mi Plata Clara está disponible en http://localhost:${port}`);
});
