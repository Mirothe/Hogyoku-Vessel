// the page-load sequence on the headline and the reveals further down. the hidden starting states live in the css
// and only apply while this runs and the visitor hasn't asked for less motion

const root = document.documentElement;

const headline = document.querySelector(".cinema h1");
if (headline) {
  const words = headline.textContent.trim().split(/\s+/);
  headline.setAttribute("aria-label", words.join(" "));
  headline.innerHTML = words.map((w, i) => `<span class="w" aria-hidden="true" style="--i:${i}"><span>${w}</span></span>`).join(" ");
}

// wait for the fonts so the headline doesn't rise in one face and land in another
const go = () => requestAnimationFrame(() => root.classList.add("ready"));
(document.fonts ? document.fonts.ready : Promise.resolve()).then(go);
setTimeout(go, 1500);

document.querySelectorAll("[data-stagger]").forEach((group) => {
  [...group.children].forEach((child, i) => {
    child.setAttribute("data-reveal", "");
    child.style.setProperty("--i", i);
  });
});
document.querySelectorAll(".line li").forEach((li, i) => li.style.setProperty("--i", i));

const seen = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    e.target.classList.add("in");
    seen.unobserve(e.target);
  }
}, { threshold: 0.15, rootMargin: "0px 0px -8% 0px" });
document.querySelectorAll("[data-reveal], .line, .rows li").forEach((el) => seen.observe(el));

// the header floats clear over the sky and turns solid once the page scrolls under it
const bar = document.querySelector(".bar.over");
if (bar) {
  const solid = () => bar.classList.toggle("scrolled", scrollY > 40);
  addEventListener("scroll", solid, { passive: true });
  solid();
}
