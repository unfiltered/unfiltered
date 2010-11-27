package unfiltered.oauth

import unfiltered.response.Html

trait OAuthTemplates {
  def layout(body: xml.NodeSeq) = Html(
    <html>
      <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
      <head>
        <title>oauth template</title>
      </head>
      <body>
       {body}
      </body>
    </html>
  )
}
