---
title: "Okta SSO Setup"
description: "Configure Okta as a single sign-on provider for your Kinotic organization."
canonical_url: "https://kinotic.ai/organizations/okta-sso-setup"
---
# Okta SSO Setup

> Configure Okta as a single sign-on provider for your Kinotic organization.

This guide connects an Okta org to your Kinotic organization so members can sign in to the Kinotic portal with **Sign in with Okta**.

The setup goes back and forth once: you create the app in Okta, add the provider in Kinotic, then copy Kinotic's callback URL back into Okta.

## Prerequisites

- Administrator access to your **Okta Admin Console** (for example `https://yourorg-admin.okta.com`).
- Administrator access to your **Kinotic organization**.

## 1. Create the app integration in Okta

1. In the Okta Admin Console, go to **Applications > Applications** and click **Create App Integration**.
2. Select the **Classic experience** tab, choose **OIDC - OpenID Connect** as the sign-in method, and **Web Application** as the application type. Click **Next**.

![Create app integration dialog](https://kinotic.ai/images/docs/okta/01-create-app-integration.png)

![OIDC - OpenID Connect and Web Application selected](https://kinotic.ai/images/docs/okta/02-oidc-web-application.png)

1. Enter an **App integration name**, for example `Kinotic`, and under **Client definition** choose **Use Okta-generated client ID**.

![App integration name](https://kinotic.ai/images/docs/okta/03-app-name.png)

1. Under **Grant type**, keep **Authorization Code** checked.
2. Leave **Sign-in redirect URIs** as Okta's placeholder (`http://localhost:8080/authorization-code/callback`) for now. You will replace it with Kinotic's callback URL in step 4.

![Grant type and redirect URIs](https://kinotic.ai/images/docs/okta/04-grant-type-redirect-uris.png)

1. Under **Assignments**, choose **Allow everyone in your organization to access**, or **Limit access to selected groups** to restrict who can sign in. Click **Save**.

![Assignments](https://kinotic.ai/images/docs/okta/05-assignments.png)

## 2. Copy the client credentials

On the saved app's **General** tab, find **Client Credentials**:

- Copy the **Client ID**.
- Under **Client Secrets**, use the copy button to copy the secret.

![Client credentials](https://kinotic.ai/images/docs/okta/06-client-credentials.png)

<alert type="warning">

Treat the client secret like a password. Paste it directly into Kinotic; don't share it in chat or screenshots.

</alert>

## 3. Add the provider in Kinotic

1. In the Kinotic portal, open **Organization settings** and select the **Authentication providers** tab. Click **Add provider**.

![Authentication providers tab](https://kinotic.ai/images/docs/okta/07-kinotic-providers-empty.png)

1. Fill in the form:

<table>
<thead>
  <tr>
    <th>
      Field
    </th>
    
    <th>
      Value
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <strong>
        Provider
      </strong>
    </td>
    
    <td>
      <code>
        Okta
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Display name
      </strong>
    </td>
    
    <td>
      The text on the login button, for example <code>
        Okta
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Okta domain
      </strong>
    </td>
    
    <td>
      Your Okta org domain, for example <code>
        yourorg.okta.com
      </code>
      
       -- no <code>
        https://
      </code>
      
       and no path
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Client ID
      </strong>
    </td>
    
    <td>
      The client ID from Okta
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Client secret
      </strong>
    </td>
    
    <td>
      The client secret from Okta
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Public client (no secret)
      </strong>
    </td>
    
    <td>
      Leave unchecked. Check it only for a public client that has no secret
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Enabled
      </strong>
    </td>
    
    <td>
      On, so the login page offers this provider
    </td>
  </tr>
  
  <tr>
    <td>
      <strong>
        Advanced > Audience
      </strong>
    </td>
    
    <td>
      Optional. Set it only when the <code>
        aud
      </code>
      
       claim of Okta's tokens is not the client ID
    </td>
  </tr>
</tbody>
</table>

![Add provider form](https://kinotic.ai/images/docs/okta/08-kinotic-add-provider.png)

![Advanced section with Audience](https://kinotic.ai/images/docs/okta/09-kinotic-add-provider-advanced.png)

1. Click **Add provider**.

Kinotic creates the configuration and the drawer shows two URLs, each with a copy button:

- **Callback URL** -- `https://<api-host>/api/auth/org/login/sso/callback/<config-id>`
- **Direct sign-in link** -- `https://<api-host>/api/auth/org/login/<org>/start/<config-id>`, useful for a tile in Okta's app launcher.

Copy the **Callback URL**.

![Provider added with callback URL](https://kinotic.ai/images/docs/okta/10-kinotic-callback-url.png)

<alert type="info">

The configuration id at the end of the callback URL is created on the first save, so the callback URL only exists after you click **Add provider**.

</alert>

## 4. Register the callback URL in Okta

1. Back in Okta, open the app's **General** tab, and click **Edit** on **General Settings**.
2. Under **LOGIN**, replace the **Sign-in redirect URIs** placeholder with the callback URL you copied from Kinotic.
3. Click **Save**.

![Sign-in redirect URI saved](https://kinotic.ai/images/docs/okta/11-okta-redirect-uri-saved.png)

<alert type="warning">

Okta matches redirect URIs exactly. Paste the whole callback URL, including the configuration id at the end.

</alert>

The provider now appears as **Enabled** in Kinotic:

![Okta provider enabled](https://kinotic.ai/images/docs/okta/12-kinotic-provider-enabled.png)

## 5. Give people access

Okta decides who may authenticate, and Kinotic decides who belongs to your organization. An Okta sign-in gets in only when it matches a member of your organization, so assigning someone to the Okta app alone does not let them in.

- **New members.** In Kinotic, open **Members** and click **Add member**, using the email address the person has in Okta. With **Send an invitation email** off, their first **Sign in with Okta** admits them: Kinotic matches the email Okta verified to the member you added and links their Okta account to it.
- **People who already sign in.** Someone who already signs in to your organization another way, such as you after creating the organization with GitHub, is not matched by email. They sign in as they do now, open **Profile**, and under **Sign-in methods** choose Okta from **Connect another way to sign in**. From then on, either method signs them in.

Each person must also be assigned to the Okta app in Okta, as set up in step 1.

## 6. Test sign-in

1. Open your organization's login page, `https://<portal-host>/login/<org>`. It now shows **Sign in with Okta** (using your display name).
2. Sign in as one of the people from step 5.

## Troubleshooting

<table>
<thead>
  <tr>
    <th>
      Symptom
    </th>
    
    <th>
      Cause and fix
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Okta shows a <code>
        redirect_uri
      </code>
      
       error
    </td>
    
    <td>
      The sign-in redirect URI in Okta doesn't exactly match Kinotic's callback URL. Copy it again from the provider in Kinotic and replace it in Okta
    </td>
  </tr>
  
  <tr>
    <td>
      Okta says the user isn't assigned to the app
    </td>
    
    <td>
      Assign the user, or a group they belong to, under the app's <strong>
        Assignments
      </strong>
      
       tab
    </td>
  </tr>
  
  <tr>
    <td>
      The login page doesn't show the Okta button
    </td>
    
    <td>
      Check that the provider is <strong>
        Enabled
      </strong>
      
       under <strong>
        Organization settings > Authentication providers
      </strong>
    </td>
  </tr>
  
  <tr>
    <td>
      "That identity signs in to no Kinotic organization"
    </td>
    
    <td>
      The Okta account matches no member of your organization. Add the person under <strong>
        Members
      </strong>
      
       with the email address they have in Okta, or, if they already sign in another way, have them connect Okta from their <strong>
        Profile
      </strong>
      
       (step 5)
    </td>
  </tr>
  
  <tr>
    <td>
      "Okta has not verified your email address"
    </td>
    
    <td>
      Okta reports the person's email address as unverified. Have them verify it in Okta, for example by completing Okta's activation or password-reset email, then sign in again
    </td>
  </tr>
  
  <tr>
    <td>
      "You cannot sign in to this organization"
    </td>
    
    <td>
      The member is disabled. Enable them under <strong>
        Members
      </strong>
    </td>
  </tr>
</tbody>
</table>


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
