package org.twak.tweed.gen.skel;

import java.util.Collections;
import java.util.List;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.tweed.gen.Pointz;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.collections.DHash;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.LinearForm3D;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.HasSuper;
import org.twak.viewTrace.franken.RoofGreebleApp;
import org.twak.viewTrace.franken.RoofSuperApp;
import org.twak.viewTrace.franken.RoofTexApp;
import org.twak.viewTrace.franken.SuperSuper;

public class MiniRoof implements HasSuper {

	public DHash<Loop<Point2d>, Face> origins = new DHash<>();
	
	public LoopL<Point2d> boundary = new LoopL<>();
	public LoopL<Point2d> pitches = new LoopL<>(), flats = new LoopL<>();
	
	public DRectangle bounds;
	
	public MultiMap<Loop<Point2d>, FCircle> greebles;
	
	public SuperFace superFace;

	public RoofGreebleApp roofGreebleApp = new RoofGreebleApp( this );
	public RoofTexApp roofTexApp = new RoofTexApp(this);
	public RoofSuperApp roofSuperApp = new RoofSuperApp( this );
	
	public MiniRoof( SuperFace superFace ) {
		this.superFace = superFace;
	}
	
	public void addFeature( RoofGreeble f, double radius, Point2d worldXY ) {
		
		for (Loop<Point2d> face : getAllFaces() ) {
			if (Loopz.inside( worldXY, face )) {
				
				FCircle circ = new FCircle (worldXY, Mathz.clamp (radius * 0.6, 0.1, 0.4 ), f );
				circ.veluxTextureApp.parent = roofTexApp;
				circ.veluxTextureApp.appMode = roofTexApp.appMode;
				
				// 1. try moving the feature away from the edge
				for (Loopable<Point2d> pt : face.loopableIterator()) {
					
					Line l = new Line (pt.get(), pt.getNext().get()) ;
					
					double dist = l.distance( circ.loc, true );
					
					if (dist < circ.radius) {
						Vector2d perp = new Vector2d(l.dir());
						perp.set (-perp.y, perp.x);
						perp.scale ( (circ.radius - dist) / perp.length());
						
						circ.loc.add( perp );
					}
				}

				
				// 2. otherwise shrink the feature
				for (Loopable<Point2d> pt : face.loopableIterator()) {
					Line l = new Line (pt.get(), pt.getNext().get()) ;
					circ.radius = Math.min (circ.radius, l.distance( circ.loc, true ));
				}
				
				if (circ.radius < 0.1)
					return;

				for (HalfEdge e : superFace ) {
					
					SuperEdge se = (SuperEdge)e;
					
					for (FRect fr : se.toEdit.featureGen.getRects( Feature.WINDOW ) ) // avoid dormer windows
						for (Point2d a : circ.toRect(). points())
							if ( Loopz.inside( a,  fr.panesLabelApp.coveringRoof ) )
								return;
				}
				
				greebles.put (face, circ );
			}
		}
		
	}

	public void setOutline( Output output ) {
		
		pitches = new LoopL<>();
		flats = new LoopL<>();
		origins.clear();
		boundary.clear();
		pitches.clear();
		flats.clear();
		
		clearGreebles();		
		
		for (Face f : output.faces.values() ) {
			
			
			if (f.edge.linearForm == null /*! fixme !*/ ) {
				
				Vector2d a2 = f.edge.projectDown().dir();
				Vector3d b = new Vector3d ( f.edge.uphill ), a = new Vector3d(a2.x, a2.y, 0);
				
				b.cross( a, b );
				
				f.edge.linearForm = new LinearForm3D( b, f.edge.start );
			}
			
//			if ( f.edge.getPlaneNormal().angle(Mathz.Z_UP) > Math.PI /2 -0.001 ) // GreebleHelper.getTag( f.profile, RoofTag.class ) == null )
			if ( GreebleHelper.getTag( f.profile, WallTag.class ) != null )
				continue;
			
			LoopL<Point2d> category = f.edge.uphill.angle( Mathz.Z_UP ) > Math.PI * 0.4 ? flats : pitches;
			
			LoopL<Point2d> loopl = f.points.new Map<Point2d>() {
				@Override
				public Point2d map( Loopable<Point3d> input ) {
					return Pointz.to2XY( input.get() );
				}
			}.run();
			
			for (Loop<Point2d> lop : loopl)
				origins.put (lop, f);
			
			category.addAll( loopl );
		}
		
		LoopL<Point2d> all = new LoopL<>();
		
		all.addAll( flats );
		all.addAll( pitches );
		
		boundary = Loopz.removeInnerEdges( all );
		
		bounds = new DRectangle.Enveloper();
		for (Loop<Point2d> l : pitches)
			for (Point2d p : l) 
				bounds.envelop( p );
		
		for (Loop<Point2d> l : flats)
			for (Point2d p : l) 
				bounds.envelop( p );
	}


	public void clearGreebles() {
		if (greebles != null)
			greebles.clear();
		else
			greebles = new MultiMap<>();
	}

	public LoopL<Point2d> getAllFaces() {
		
		LoopL<Point2d> out = new LoopL();
		
		out.addAll( pitches );
		out.addAll( flats);
		
		return out;
	}

	public List<FCircle> getGreebles( Face f ) {

		if (greebles == null || origins.teg(f) == null )
			return Collections.emptyList();
			
		return greebles.get( origins.teg( f ) );
	}

	@Override
	public SuperSuper getSuper() {
		return roofSuperApp;
	}
}
