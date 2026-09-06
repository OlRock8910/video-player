/* Small DOM helpers, so app.js can read as layout rather than as plumbing. */

export const $ = (id) => document.getElementById(id);

/**
 * el("button.tab", { onclick }, "Songs")
 * Tag may carry classes: "div.row.playing".
 */
export function el(spec, props = {}, ...children) {
  const [tag, ...classes] = spec.split(".");
  const node = document.createElement(tag || "div");
  if (classes.length) node.className = classes.join(" ");

  for (const [key, value] of Object.entries(props)) {
    if (value === null || value === undefined || value === false) continue;
    if (key.startsWith("on") && typeof value === "function") {
      node.addEventListener(key.slice(2), value);
    } else if (key === "class") {
      node.className = node.className ? `${node.className} ${value}` : value;
    } else if (key === "text") {
      node.textContent = value;
    } else if (key === "html") {
      node.innerHTML = value;
    } else if (key in node && key !== "list") {
      node[key] = value;
    } else {
      node.setAttribute(key, value === true ? "" : value);
    }
  }

  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
  return node;
}

let toastTimer = null;

export function toast(message) {
  const box = $("toast");
  box.textContent = message;
  box.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    box.hidden = true;
  }, 2600);
}

/** A one-off popup menu anchored near the pointer, dismissed on the next click. */
export function contextMenu(event, items) {
  document.querySelector(".menu")?.remove();
  const menu = el(
    "div.menu",
    {},
    items.map((item) =>
      el("button", {
        text: item.label,
        onclick: () => {
          menu.remove();
          item.run();
        },
      }),
    ),
  );

  menu.style.visibility = "hidden";
  document.body.append(menu);
  const box = menu.getBoundingClientRect();
  const x = Math.min(event.clientX, window.innerWidth - box.width - 10);
  const y = Math.min(event.clientY, window.innerHeight - box.height - 10);
  menu.style.left = `${Math.max(8, x)}px`;
  menu.style.top = `${Math.max(8, y)}px`;
  menu.style.visibility = "visible";

  const close = (e) => {
    if (!menu.contains(e.target)) {
      menu.remove();
      document.removeEventListener("pointerdown", close, true);
    }
  };
  setTimeout(() => document.addEventListener("pointerdown", close, true), 0);
}

/**
 * Sets an <img> from an async source, ignoring results that arrive after the
 * element has been pointed at something else.
 */
export async function setArt(img, promise, fallback = "./icons/icon-192.png") {
  const token = (img.__token || 0) + 1;
  img.__token = token;
  const url = await promise;
  if (img.__token !== token) return;
  img.src = url || fallback;
}
