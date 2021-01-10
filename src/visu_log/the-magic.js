{
  let x = document.getElementsByClassName("commit_branches");


  for (let i = 0; i < x.length; i++) {
    let currentXDelta = -1;
    let groupNodes = x[i].querySelectorAll('g');
    for (let gNode = 0; gNode < groupNodes.length; gNode++) {
      let textNode = groupNodes[gNode].querySelector('text');
      let rectNode = groupNodes[gNode].querySelector('rect');
      if (!textNode || !rectNode)
        continue;
      if (currentXDelta < 0)
        currentXDelta = textNode.getAttribute("x");
      else
        textNode.setAttribute("x", currentXDelta);
      let f = textNode.getBBox();
      currentXDelta = f.x + f.width + 10;
      rectNode.setAttribute("width", f.width + 4);
      rectNode.setAttribute("height", f.height + 4);
      rectNode.setAttribute("x", f.x - 2);
      rectNode.setAttribute("y", f.y - 2);
    }
  }
}


{
  let x = document.getElementsByClassName("commit_g");


  for (let i = 0; i < x.length; i++) {
    let groupNode = x[i].querySelector('g');
    if (!groupNode)
      continue;

    let commitCircle = document.querySelector('.' + groupNode.classList[0]);
    if (!commitCircle)
      continue;

    commitCircle.addEventListener("mouseenter", function () {
      groupNode.classList.add("show_group");
    })

    commitCircle.addEventListener("mouseleave", function () {
      groupNode.classList.remove("show_group");
    })

    commitCircle.addEventListener("mouseup", function () {
      groupNode.classList.toggle("show_group_click");
    })

    groupNode.addEventListener("mouseup", function () {
      const selection = window.getSelection();
      if (!selection || selection.isCollapsed)
        groupNode.classList.toggle("show_group_click");
    })
  }
}