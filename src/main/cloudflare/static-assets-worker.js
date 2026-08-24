export default {
  async fetch(request, env) {
    const assetUrl = new URL(request.url);
    assetUrl.searchParams.set("__axi_asset_version", "20260824-103");
    const response = await env.ASSETS.fetch(new Request(assetUrl, request));
    const headers = new Headers(response.headers);
    const type = headers.get("content-type") || "";

    if (type.includes("text/html") || type.includes("text/css") || type.includes("javascript")) {
      headers.set("cache-control", "no-store, max-age=0");
    }

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers
    });
  }
};
