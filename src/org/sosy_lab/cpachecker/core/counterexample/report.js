$(function() {

	// JSON_INPUT

	// variable declarations
	var nodes = json.nodes;
	var edges = json.edges;
	var functions = json.functionNames;
	var combinedNodes = json.combinedNodes;
	var functionCallEdges = json.functionCallEdges;
	var errorPath;
	if (json.hasOwnProperty("errorPath"))
		erroPath = json.errorPath;
	var requiredGraphs = 1;
	var graphSplitThreshold = 700; // TODO: allow user input with max value 1000
	var zoomEnabled = false; // TODO: allow user to witch between zoom possibilities

	// TODO: different edge weights based on type?
	var constants = {
		blankEdge : "BlankEdge",
		assumeEdge : "AssumeEdge",
		statementEdge : "StatementEdge",
		declarationEdge : "DeclarationEdge",
		returnStatementEdge : "ReturnStatementEdge",
		functionCallEdge : "FunctionCallEdge",
		functionReturnEdge : "FunctionReturnEdge",
		callToReturnEdge : "CallToReturnEdge",
		entryNode : "entry",
		exitNode : "exit",
		afterNode : "after",
		beforeNode : "before",
		margin : 20,
	}

	if (nodes.length > graphSplitThreshold) {
		buildMultipleGraphs();
	} else {
		buildSingleGraph();
	}
	
	//TODO: Test
	function buildMultipleGraphs() {
		requiredGraphs = Math.floor(nodes.length/graphSplitThreshold);
		var remainder = nodes.length % graphSplitThreshold;
		if (remainder > 0) requiredGraphs++;
		var firstGraphBuild = false;
		var nodesPerGraph
		for (var i in requiredGraphs) {
			d3.select("#body").append("div").attr("id", "graph" + i);
			if (!firstGraphBuild) {
				nodesPerGraph = nodes.slice(0, graphSplitThreshold + i);
				firstGraphBuild = true;
			} else {
				if (nodes[graphSplitThreshold * i + 1] !== undefined) 
					nodesPerGraph = nodes.slice(graphSplitThreshold * (i - 1) + 1, graphSplitThreshold * i + 1)
				else
					nodesPerGraph = nodes.slice(graphSplitThreshold * (i - 1) + 1)
			}
			var g = new dagreD3.graphlib.Graph().setGraph({}).setDefaultEdgeLabel(
					function() {
						return {};
					});
			g.graph().transition = function(selection) {
				return selection.transition().duration(500);
			};
			setGraphNodes(g, nodesPerGraph);
			roundNodeCorners(g);
			setGraphEdges(g, edges); // TODO: edgesPerGraph
			var render = new dagreD3.render();
			var svg = d3.select("#graph" + i).append("svg").attr("id", "svg" + i), svgGroup = svg
					.append("g");
			render(d3.select("#svg" + i + " g"), g);
			svg.attr("height", g.graph().height + constants.margin * 2);
			svg.attr("width", g.graph().width * 2 + constants.margin * 2);
			var xCenterOffset = (svg.attr("width") / 2);
			svgGroup.attr("transform", "translate(" + xCenterOffset + ", 20)");
			addEventsToNodes();
			addEventsToEdges();
		}
		
	}

	// build single graph for all contained nodes
	function buildSingleGraph() {
		d3.select("#body").append("div").attr("id", "graph");
		// Create the graph
		var g = new dagreD3.graphlib.Graph().setGraph({}).setDefaultEdgeLabel(
				function() {
					return {};
				});
		g.graph().transition = function(selection) {
			return selection.transition().duration(500);
		};
		setGraphNodes(g, nodes);
		roundNodeCorners(g);
		setGraphEdges(g, edges);
		// Create the renderer
		var render = new dagreD3.render();
		// Set up an SVG group so that we can translate the final graph.
		var svg = d3.select("#graph").append("svg").attr("id", "svg"), svgGroup = svg
				.append("g");
		// Set up zoom support //TODO: own function pass the svgGroup
		var zoom = d3.behavior.zoom().on(
				"zoom",
				function() {
					svgGroup.attr("transform", "translate("
							+ d3.event.translate + ")" + "scale("
							+ d3.event.scale + ")");
				});
		svg.call(zoom);
		// Run the renderer. This is what draws the final graph.
		render(d3.select("#svg g"), g);
		// Center the graph - calculate svg.attributes
		svg.attr("height", g.graph().height + constants.margin * 2);
		svg.attr("width", g.graph().width * 2 + constants.margin * 2);
		var xCenterOffset = (svg.attr("width") / 2);
		svgGroup.attr("transform", "translate(" + xCenterOffset + ", 20)");
		addEventsToNodes();
		addEventsToEdges();
	}

	// Set nodes for the graph
	function setGraphNodes(graph, nodesToSet) {
		nodesToSet.forEach(function(n) {
			graph.setNode(n.index, {
				label : "N" + n.index,
				class : n.type + "-node",
				id : "node" + n.index,
				shape : nodeShapeDecider(n)
			});
		})
	}

	// Add desired events to the nodes
	function addEventsToNodes() {
		d3.selectAll("svg g.node").on("mouseover", function(d){showInfoBoxNode(d3.event, d);})
			.on("mouseout", function(){hideInfoBoxNode();})
		// TODO: node.on("click") needed?
	}

	// Round the corners of rectangle shaped nodes
	function roundNodeCorners(graph) {
		graph.nodes().forEach(function(it) {
			var item = graph.node(it);
			item.rx = item.ry = 5;
		});
	}

	// Decide the shape of the nodes based on type
	function nodeShapeDecider(n) {
		if (n.type === constants.entryNode)
			return "diamond";
		else
			return "rect";
	}

	function setGraphEdges(graph, edgesToSet) {
		edgesToSet.forEach(function(e) {
			// TODO: different arrowhead for different type + class function
			// (for errorPath)
			graph.setEdge(e.source, e.target, {
				label : e.source + "->" + e.target,
				class : e.type,
				id : "edge" + e.source + e.target,
				weight : edgeWeightDecider(e)
			});
		})
	}

	// Add desired events to edge
	function addEventsToEdges() {
		d3.selectAll("svg g.edgePath").on("mouseover", function(d){showInfoBoxEdge(d3.event, d);})
			.on("mouseout", function(){hideInfoBoxEdge();})
		// TODO: edge.on("click")
	}

	// Decide the weight for the edges based on type
	function edgeWeightDecider(edge) {
		if (edge.type === constants.functionCallEdge)
			return 1;
		else
			return 10;
	}

	// on mouse over display info box for node
	function showInfoBoxNode(e, nodeIndex) {
		var offsetX = 20;
		var offsetY = 0;
		var positionX = e.pageX;
		var positionY = e.pageY;
		var node = nodes.find(function(n) {return n.index == nodeIndex;});
		var displayInfo = "function: " + node.func + "<br/>" + "type: " + node.type;
		d3.select("#boxContentNode").html("<p>" + displayInfo + "</p>");
		d3.select("#infoBoxNode").style("left", function() {
			return positionX + offsetX + "px";
		}).style("top", function() {
			return positionY + offsetY + "px";
		}).style("visibility", "visible");
	}

	// on mouse out hide the info box for node
	function hideInfoBoxNode() {
		d3.select("#infoBoxNode").style("visibility", "hidden");
	}

	// on mouse over display info box for edge
	function showInfoBoxEdge(e, edgeIndex) {
		var offsetX = 20;
		var offsetY = 0;
		var positionX = e.pageX;
		var positionY = e.pageY;
		// line, file, stmt, type
		var edge = edges.find(function(e){return e.source == edgeIndex.v && e.target == edgeIndex.w;});
		var displayInfo = "line: " + edge.line+ "<br/>" + "file: " + edge.file + "<br/>" + "stmt: " + edge.stmt + "<br/>" + "type: " + edge.type;
		d3.select("#boxContentEdge").html("<p>" + displayInfo + "</p>");
		d3.select("#infoBoxEdge").style("left", function() {
			return positionX + offsetX + "px";
		}).style("top", function() {
			return positionY + offsetY + "px";
		}).style("visibility", "visible");
	}

	// on mouse out hide info box for edge
	function hideInfoBoxEdge() {
		d3.select("#infoBoxEdge").style("visibility", "hidden");
	}

});