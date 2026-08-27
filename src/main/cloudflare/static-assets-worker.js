export default {
  async fetch(request, env) {
    const assetUrl = new URL(request.url);
    if ((assetUrl.pathname === "/p" || assetUrl.pathname === "/p/") && assetUrl.searchParams.has("j")) {
      const legacySection = assetUrl.searchParams.get("j");
      let destination = "/v2/index.html";
      if (legacySection === "23") destination = "/v2/site/class/index.html";
      if (legacySection === "87") {
        destination = assetUrl.searchParams.get("pno") === "9"
          ? "/v2/site/class/review.html?id=20126"
          : "/v2/site/class/reviews.html";
      }
      return Response.redirect(assetUrl.origin + destination, 301);
    }
    assetUrl.searchParams.set("__axi_asset_version", "20260827-120");
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
