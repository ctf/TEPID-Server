<%@ page import="ca.mcgill.science.tepid.server.server.Config" %>

<!-- HTML for static distribution bundle build -->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Swagger UI</title>
    <link rel="stylesheet" type="text/css" href="/tepid/webjars/swagger-ui/3.20.5/swagger-ui.css" >
    <link rel="icon" type="image/png" href="/tepid/webjars/swagger-ui/3.20.5/favicon-32x32.png" sizes="32x32" />
    <link rel="icon" type="image/png" href="/tepid/webjars/swagger-ui/3.20.5/favicon-16x16.png" sizes="16x16" />
    <style>
        html
        {
            box-sizing: border-box;
            overflow: -moz-scrollbars-vertical;
            overflow-y: scroll;
        }

        *,
        *:before,
        *:after
        {
            box-sizing: inherit;
        }

        body
        {
            margin:0;
            background: #fafafa;
        }
    </style>
</head>

<body>
<div id="swagger-ui"></div>

<script src="/tepid/webjars/swagger-ui/3.20.5/swagger-ui-bundle.js"> </script>
<script src="/tepid/webjars/swagger-ui/3.20.5/swagger-ui-standalone-preset.js"> </script>
<script>
    window.onload = function() {
        // Begin Swagger UI call region
        const ui = SwaggerUIBundle({
            url: "<%out.print("https://"+Config.INSTANCE.getTEPID_URL_PRODUCTION()+"/openapi.json"); %>",
            dom_id: '#swagger-ui',
            deepLinking: true,
            presets: [
                SwaggerUIBundle.presets.apis,
                SwaggerUIStandalonePreset
            ],
            plugins: [
                SwaggerUIBundle.plugins.DownloadUrl
            ],
            layout: "StandaloneLayout"
        });
        // End Swagger UI call region

        window.ui = ui
    }
</script>
</body>
</html>